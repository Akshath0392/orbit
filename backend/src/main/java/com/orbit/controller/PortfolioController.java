package com.orbit.controller;

import com.orbit.domain.client.Client;
import com.orbit.domain.client.Portfolio;
import com.orbit.domain.client.Project;
import com.orbit.repository.ClientRepository;
import com.orbit.repository.GovernanceMeetingRepository;
import com.orbit.repository.PortfolioRepository;
import com.orbit.repository.ProjectReleaseRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.service.ProjectHealthService;
import com.orbit.service.dashboard.PortfolioDashboardService;

import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioRepository           portfolios;
    private final ClientRepository              clients;
    private final ProjectRepository             projects;
    private final ProjectHealthService          health;
    private final PortfolioDashboardService     dashboard;
    private final ProjectReleaseRepository      releases;
    private final GovernanceMeetingRepository   governance;

    public PortfolioController(PortfolioRepository portfolios, ClientRepository clients,
                                ProjectRepository projects,
                                ProjectHealthService health,
                                PortfolioDashboardService dashboard,
                                ProjectReleaseRepository releases,
                                GovernanceMeetingRepository governance) {
        this.portfolios = portfolios; this.clients = clients; this.projects = projects;
        this.health = health; this.dashboard = dashboard;
        this.releases = releases; this.governance = governance;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> list(@RequestParam(required=false) Long clientId) {
        List<Portfolio> list = clientId != null
            ? portfolios.findByClientsIdAndActiveTrue(clientId)
            : portfolios.findByActiveTrue();

        // One bulk health preload for every active project instead of ~8
        // queries per project.
        List<Project> allActive = projects.findByActiveTrue();
        Map<Long,List<Project>> byPortfolio = allActive.stream()
            .filter(p -> p.getPortfolio() != null)
            .collect(Collectors.groupingBy(p -> p.getPortfolio().getId()));
        Map<Long,Integer> pctById = allActive.isEmpty() ? Map.of() : health.healthPctAll(allActive);

        return list.stream().map(p -> {
            Map<String,Object> m = toMap(p);
            List<Project> projs = byPortfolio.getOrDefault(p.getId(), List.of());
            int avg = projs.isEmpty() ? 100
                : (int) Math.round(projs.stream()
                    .mapToInt(pr -> pctById.getOrDefault(pr.getId(), 100)).average().orElse(100));
            m.put("projectCount", projs.size());
            m.put("healthPct",    avg);
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/{id}/kpis")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> kpis(@PathVariable Long id) {
        return dashboardSlice(id, "kpis");
    }

    @GetMapping("/{id}/accounts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> accounts(@PathVariable Long id) {
        return dashboardSlice(id, "accounts");
    }

    @GetMapping("/{id}/exceptions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> exceptions(@PathVariable Long id) {
        return dashboardSlice(id, "exceptions");
    }

    @GetMapping("/{id}/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> summary(@PathVariable Long id) {
        return dashboardSlice(id, "summary");
    }

    // Bundled dashboard: summary + kpis + accounts + exceptions in one
    // response so RadarPage fires one request instead of four.
    @GetMapping("/{id}/dashboard")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> dashboardBundle(@PathVariable Long id) {
        Map<String,Object> full = dashboard.dashboard(id);
        if (full == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(full);
    }

    private ResponseEntity<?> dashboardSlice(Long id, String section) {
        Map<String,Object> full = dashboard.dashboard(id);
        if (full == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(full.get(section));
    }

    @GetMapping("/{id}/projects")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> portfolioProjects(@PathVariable Long id) {
        return projects.findByPortfolioIdAndActiveTrue(id).stream().map(p -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",               p.getId());
            m.put("name",             p.getName());
            m.put("clientId",         p.getClient() != null ? p.getClient().getId() : null);
            m.put("clientName",       p.getClient() != null ? p.getClient().getName() : "");
            m.put("portfolioId",      p.getPortfolio() != null ? p.getPortfolio().getId() : null);
            m.put("jiraProjectKeys",  p.getJiraProjectKeys());
            m.put("jiraJqlOverride",  p.getJiraJqlOverride());
            m.put("jiraCrFilter",     p.getJiraCrFilter());
            m.put("jiraBugFilter",    p.getJiraBugFilter());
            m.put("isSharedProdBugs", p.isSharedProdBugs());
            m.put("clientCodeField",  p.getClientCodeField());
            return m;
        }).collect(Collectors.toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> create(@RequestBody Map<String,Object> body) {
        Portfolio p = new Portfolio();
        p.setName((String) body.get("name"));
        p.setDescription((String) body.get("description"));
        p.setClients(resolveClients(body));
        Portfolio saved = portfolios.save(p);
        return ResponseEntity.ok(toMap(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        return portfolios.findById(id).map(p -> {
            if (body.containsKey("name"))        p.setName((String) body.get("name"));
            if (body.containsKey("description")) p.setDescription((String) body.get("description"));
            if (body.containsKey("active"))      p.setActive(Boolean.TRUE.equals(body.get("active")));
            if (body.containsKey("clientIds") || body.containsKey("clientId"))
                p.setClients(resolveClients(body));
            portfolios.save(p);
            return ResponseEntity.ok(toMap(p));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> delete(@PathVariable Long id) {
        portfolios.findById(id).ifPresent(p -> { p.setActive(false); portfolios.save(p); });
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/projects/{projectId}")
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> assignProject(@PathVariable Long id, @PathVariable Long projectId) {
        Optional<Portfolio> portfolio = portfolios.findById(id);
        Optional<Project>   project   = projects.findById(projectId);
        if (portfolio.isEmpty() || project.isEmpty()) return ResponseEntity.notFound().build();
        project.get().setPortfolio(portfolio.get());
        projects.save(project.get());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/{id}/projects/{projectId}")
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> removeProject(@PathVariable Long id, @PathVariable Long projectId) {
        projects.findById(projectId).ifPresent(p -> { p.setPortfolio(null); projects.save(p); });
        return ResponseEntity.noContent().build();
    }

    // ── Release readiness (portfolio-scoped, next N days) ─────────────────────
    @GetMapping("/{id}/releases")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> portfolioReleases(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "14") int days) {
        if (portfolios.findById(id).isEmpty()) return List.of();
        var projectIds = projects.findByPortfolioIdAndActiveTrue(id).stream().map(Project::getId).toList();
        if (projectIds.isEmpty()) return List.of();
        LocalDate from = LocalDate.now();
        LocalDate to   = from.plusDays(days);
        return releases.findByProjectIdInAndReleaseDateBetweenOrderByReleaseDateAsc(projectIds, from, to)
            .stream().map(r -> {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("id",        r.getId());
                m.put("projectId", r.getProjectId());
                m.put("date",      r.getReleaseDate());
                m.put("type",      r.getReleaseType());
                m.put("label",     r.getLabel());
                m.put("rag",       r.getRag());
                return m;
            }).toList();
    }

    // ── Governance meetings (portfolio-scoped) ────────────────────────────────
    @GetMapping("/{id}/governance")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> portfolioGovernance(@PathVariable Long id) {
        if (portfolios.findById(id).isEmpty()) return List.of();
        var projectIds = projects.findByPortfolioIdAndActiveTrue(id).stream().map(Project::getId).toList();
        var portfolioLevel = governance.findByPortfolioIdOrderByNextDueAsc(id);
        var projectLevel   = projectIds.isEmpty() ? List.<com.orbit.domain.account.GovernanceMeeting>of()
            : governance.findByProjectIdInOrderByNextDueAsc(projectIds);
        List<Map<String,Object>> out = new ArrayList<>();
        for (var g : portfolioLevel) out.add(govToMap(g));
        for (var g : projectLevel)   out.add(govToMap(g));
        return out;
    }

    private Map<String,Object> govToMap(com.orbit.domain.account.GovernanceMeeting g) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",          g.getId());
        m.put("scope",       g.getProjectId() != null ? "project" : "portfolio");
        m.put("projectId",   g.getProjectId());
        m.put("cadence",     g.getCadence());
        m.put("title",       g.getTitle());
        m.put("lastHeld",    g.getLastHeld());
        m.put("nextDue",     g.getNextDue());
        m.put("owner",       g.getOwner());
        m.put("status",      g.getStatus());
        return m;
    }

    private Map<String,Object> toMap(Portfolio p) {
        Set<Client> clientList = p.getClients() != null ? p.getClients() : Set.of();
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",          p.getId());
        m.put("name",        p.getName());
        m.put("description", p.getDescription());
        m.put("active",      p.getActive());
        m.put("clientIds",   clientList.stream().map(Client::getId).collect(Collectors.toList()));
        m.put("clientNames", clientList.stream().map(Client::getName).collect(Collectors.toList()));
        // Convenience single-value fields for backward compat (first client or empty)
        Client first = clientList.stream().findFirst().orElse(null);
        m.put("clientId",   first != null ? first.getId() : null);
        m.put("clientName", first != null ? first.getName() : "");
        return m;
    }

    @SuppressWarnings("unchecked")
    private java.util.LinkedHashSet<Client> resolveClients(Map<String,Object> body) {
        var set = new java.util.LinkedHashSet<Client>();
        Object ids = body.get("clientIds");
        if (ids instanceof java.util.List<?> list) {
            list.forEach(id -> {
                if (id != null) clients.findById(Long.valueOf(id.toString())).ifPresent(set::add);
            });
        } else if (body.get("clientId") != null) {
            clients.findById(Long.valueOf(body.get("clientId").toString())).ifPresent(set::add);
        }
        return set;
    }
}
