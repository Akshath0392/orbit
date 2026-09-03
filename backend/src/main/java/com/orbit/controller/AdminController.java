package com.orbit.controller;

import com.orbit.domain.client.*;
import com.orbit.domain.config.RoleScreenConfig;
import com.orbit.repository.*;
import com.orbit.service.ProjectHealthService;
import com.orbit.service.SlaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final SlaRuleRepository slaRules;
    private final LifecycleMappingRepository lifecycleMappings;
    private final AppUserRepository users;
    private final ClientRepository clients;
    private final PortfolioRepository portfolios;
    private final ProjectRepository projects;
    private final RoleScreenConfigRepository roleConfigs;
    private final JiraIssueRepository jiraIssues;
    private final TeamRoleLabelRepository teamRoleLabels;
    private final PasswordEncoder passwordEncoder;
    private final SlaService slaService;
    private final ProjectHealthService healthService;
    private final com.orbit.service.StageCatalogService stageCatalog;

    public AdminController(SlaRuleRepository slaRules, LifecycleMappingRepository lifecycleMappings,
                            AppUserRepository users, ClientRepository clients,
                            PortfolioRepository portfolios, ProjectRepository projects,
                            RoleScreenConfigRepository roleConfigs,
                            JiraIssueRepository jiraIssues,
                            TeamRoleLabelRepository teamRoleLabels,
                            PasswordEncoder passwordEncoder,
                            SlaService slaService,
                            ProjectHealthService healthService,
                            com.orbit.service.StageCatalogService stageCatalog) {
        this.slaRules = slaRules; this.lifecycleMappings = lifecycleMappings;
        this.users = users; this.clients = clients;
        this.portfolios = portfolios; this.projects = projects;
        this.roleConfigs = roleConfigs; this.jiraIssues = jiraIssues;
        this.teamRoleLabels = teamRoleLabels;
        this.passwordEncoder = passwordEncoder;
        this.slaService = slaService;
        this.healthService = healthService;
        this.stageCatalog = stageCatalog;
    }

    // ── Lifecycle mapping auto-discovery ─────────────────────────────────────

    @PostMapping("/lifecycle-mappings/auto-discover")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> autoDiscoverMappings() {
        List<Object[]> pairs = jiraIssues.findDistinctJiraStatusAndIssueType();
        int created = 0, skipped = 0, backfilled = 0;

        for (Object[] row : pairs) {
            String jiraStatus = (String) row[0];
            String issueType  = (String) row[1];
            if (jiraStatus == null || jiraStatus.isBlank()) continue;

            if (lifecycleMappings.existsByJiraStatusAndIssueType(jiraStatus, issueType)) {
                skipped++;
            } else {
                String stage = defaultStage(jiraStatus, issueType);
                LifecycleMapping m = LifecycleMapping.builder()
                    .jiraStatus(jiraStatus).issueType(issueType).gaugeStage(stage).build();
                lifecycleMappings.save(m);
                created++;
            }

            String targetStage = defaultStage(jiraStatus, issueType);
            stageCatalog.ensureExists(targetStage);
            backfilled += jiraIssues.backfillLifecycleStage(jiraStatus, issueType, targetStage);
        }

        return ResponseEntity.ok(Map.of(
            "created", created, "alreadyExisted", skipped, "backfilled", backfilled
        ));
    }

    private String defaultStage(String jiraStatus, String issueType) {
        boolean isBug = "PROD_BUG".equals(issueType) || "UAT_BUG".equals(issueType);
        return switch (jiraStatus.toLowerCase().trim()) {
            case "backlog", "to do", "request created"           -> isBug ? "New"          : "BRD awaited";
            case "in progress", "dev in progress"                -> isBug ? "In progress"  : "In dev";
            case "blocked", "on hold", "client hold"             -> "Hold";
            case "ready for qa review - pre-prod",
                 "ready for pre-prod"                            -> isBug ? "In progress"  : "In QA";
            case "ready for production", "ready for prod"        -> isBug ? "Fixed"        : "Ready for prod";
            case "released to production", "released", "done"    -> "Released";
            case "closed"                                        -> "Closed";
            case "invalid", "won't do", "duplicate", "cancelled" -> "Closed";
            default                                              -> jiraStatus;
        };
    }

    @GetMapping("/sla-rules")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> getSlaRules() {
        return slaRules.findAllByOrderByClientIdAscSeverityAsc().stream().map(r -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",       r.getId());
            m.put("clientId", r.getClient() != null ? r.getClient().getId() : null);
            m.put("client",   r.getClient() != null ? r.getClient().getName() : "Global");
            m.put("sev",      r.getSeverity());
            m.put("resp",     r.getResponseHours() + "h");
            m.put("res",      r.getResolutionHours() + "h");
            m.put("wk",       r.getIncludeWeekends());
            return m;
        }).collect(Collectors.toList());
    }

    @PostMapping("/sla-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createSlaRule(@RequestBody Map<String,Object> body) {
        SlaRule r = new SlaRule();
        r.setSeverity((String) body.get("sev"));
        String resp = body.get("resp").toString().replace("h","");
        r.setResponseHours(new java.math.BigDecimal(resp));
        String res = body.get("res").toString().replace("h","");
        r.setResolutionHours(new java.math.BigDecimal(res));
        r.setIncludeWeekends(Boolean.TRUE.equals(body.get("wk")));
        if (body.get("clientId") != null)
            clients.findById(Long.valueOf(body.get("clientId").toString())).ifPresent(r::setClient);
        slaRules.save(r);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("id", r.getId());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/sla-rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateSlaRule(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        return slaRules.findById(id).map(r -> {
            if (body.get("sev") != null)  r.setSeverity((String) body.get("sev"));
            if (body.get("resp") != null) r.setResponseHours(new java.math.BigDecimal(body.get("resp").toString().replace("h","")));
            if (body.get("res")  != null) r.setResolutionHours(new java.math.BigDecimal(body.get("res").toString().replace("h","")));
            if (body.containsKey("wk"))   r.setIncludeWeekends(Boolean.TRUE.equals(body.get("wk")));
            if (body.get("clientId") != null)
                clients.findById(Long.valueOf(body.get("clientId").toString())).ifPresent(r::setClient);
            else if (body.containsKey("clientId"))  // explicitly null → global default
                r.setClient(null);
            slaRules.save(r);
            return ResponseEntity.ok(Map.of("id", r.getId()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/sla-rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteSlaRule(@PathVariable Long id) {
        slaRules.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sla-rules/recompute")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> recomputeSla() {
        slaService.recomputeAll();
        return ResponseEntity.ok(Map.of("status", "recomputed"));
    }

    @GetMapping("/lifecycle-mappings")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> getLifecycleMappings() {
        return lifecycleMappings.findAllByOrderByIssueTypeAscJiraStatusAsc().stream().map(m -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("jira", m.getJiraStatus());
            row.put("type", m.getIssueType());
            row.put("akki", m.getGaugeStage());
            return row;
        }).collect(Collectors.toList());
    }

    @PostMapping("/lifecycle-mappings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createMapping(@RequestBody Map<String,Object> body) {
        String jiraStatus = body.get("jira") instanceof String s ? s.trim() : "";
        if (jiraStatus.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Jira status is required"));
        // Persist only the sync vocabulary — a row saved under a display
        // label ("Bug", "UAT Bug") never matches the stage-map's exact-string key.
        String issueType = LifecycleMapping.canonicalType((String) body.get("type"));
        if (issueType == null)
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Unknown issue type — expected CR, PROD_BUG, UAT_BUG, TASK, OTHER or ALL"));
        // Upsert: re-adding an existing pair repoints its stage instead of
        // inserting a duplicate row (the edit modal deletes-then-recreates).
        LifecycleMapping m = lifecycleMappings.findFirstByJiraStatusAndIssueType(jiraStatus, issueType)
            .orElseGet(() -> LifecycleMapping.builder().jiraStatus(jiraStatus).issueType(issueType).build());
        m.setGaugeStage((String) body.get("akki"));
        lifecycleMappings.save(m);
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("id", m.getId());
        return ResponseEntity.ok(r);
    }

    @DeleteMapping("/lifecycle-mappings/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteMapping(@PathVariable Long id) {
        lifecycleMappings.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Stage catalog ─────────────────────────────────────────────────────────

    @GetMapping("/stages")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> getStages() {
        return stageCatalog.list();
    }

    @PostMapping("/stages")
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> createStage(@RequestBody Map<String,Object> body,
                                          org.springframework.security.core.Authentication auth) {
        try {
            var s = stageCatalog.create((String) body.get("name"), (String) body.get("category"),
                body.get("displayOrder") != null ? ((Number) body.get("displayOrder")).intValue() : null,
                auth != null ? auth.getName() : null);
            return ResponseEntity.ok(Map.of("id", s.getId(), "name", s.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/stages/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> updateStage(@PathVariable Long id, @RequestBody Map<String,Object> body,
                                          org.springframework.security.core.Authentication auth) {
        try {
            var s = stageCatalog.update(id, (String) body.get("name"), (String) body.get("category"),
                body.get("displayOrder") != null ? ((Number) body.get("displayOrder")).intValue() : null,
                auth != null ? auth.getName() : null);
            return ResponseEntity.ok(Map.of("id", s.getId(), "name", s.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/stages/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> deleteStage(@PathVariable Long id) {
        try {
            stageCatalog.delete(id);
            return ResponseEntity.noContent().build();
        } catch (com.orbit.service.StageCatalogService.StageInUseException e) {
            return ResponseEntity.status(409).body(Map.of(
                "error", e.getMessage(),
                "mappingCount", e.mappingCount,
                "issueCount", e.issueCount));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> getUsers() {
        return users.findAll().stream().map(u -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getName());
            m.put("email", u.getEmail());
            m.put("role", u.getRole());
            m.put("av", u.getInitials() != null ? u.getInitials() : "");
            m.put("color", u.getAvatarColor() != null ? u.getAvatarColor() : "#6366F1");
            return m;
        }).collect(Collectors.toList());
    }

    // ── Roles ────────────────────────────────────────────────────────────────
    @GetMapping("/roles")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> getRoles() {
        return roleConfigs.findAll().stream().map(r -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("roleName",    r.getRoleName());
            m.put("displayName", r.getDisplayName() != null ? r.getDisplayName() : r.getRoleName());
            m.put("screenIds",   r.getScreenIds() != null ? List.of(r.getScreenIds().split(",")) : List.of());
            m.put("chartConfig", r.getChartConfig() != null ? r.getChartConfig() : Map.of());
            return m;
        }).collect(Collectors.toList());
    }

    // Chart-config vocabulary: each key's allowed values. Trend widgets
    // default to line, breakdown charts default to bar — two type keys so one
    // role can override either family independently. runtimeToggle grants the
    // role an in-page chart-type switcher; absent = off, so existing roles
    // stay switcher-less without a data change.
    private static final Map<String, java.util.Set<String>> CHART_CONFIG_VALUES = Map.of(
        "chartType",          java.util.Set.of("line", "bar", "stacked"),
        "breakdownChartType", java.util.Set.of("bar", "line"),
        "palette",            java.util.Set.of("classic", "vibrant"),
        "runtimeToggle",      java.util.Set.of("on", "off"));

    /** Replace a role's chart preference wholesale; {} clears back to defaults. */
    @PutMapping("/roles/{roleName}/chart-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateRoleChartConfig(@PathVariable String roleName,
                                                   @RequestBody Map<String, String> body) {
        for (var e : body.entrySet()) {
            java.util.Set<String> allowed = CHART_CONFIG_VALUES.get(e.getKey());
            if (allowed == null || !allowed.contains(e.getValue()))
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid chart config " + e.getKey() + "=" + e.getValue()
                        + " (keys: " + CHART_CONFIG_VALUES.keySet() + ")"));
        }
        return roleConfigs.findByRoleName(roleName).<ResponseEntity<?>>map(r -> {
            r.setChartConfig(body.isEmpty() ? null : body);
            roleConfigs.save(r);
            return ResponseEntity.ok(Map.of("ok", true));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/roles/{roleName}/screens")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateRoleScreens(@PathVariable String roleName, @RequestBody Map<String,Object> body) {
        return roleConfigs.findByRoleName(roleName).map(r -> {
            @SuppressWarnings("unchecked")
            List<String> screens = (List<String>) body.get("screenIds");
            if (screens != null) r.setScreenIds(String.join(",", screens));
            if (body.get("displayName") != null) r.setDisplayName((String) body.get("displayName"));
            roleConfigs.save(r);
            return ResponseEntity.ok(Map.of("ok", true));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createRole(@RequestBody Map<String,Object> body) {
        RoleScreenConfig r = new RoleScreenConfig();
        r.setRoleName(((String) body.get("roleName")).toUpperCase().replace(" ","_"));
        r.setDisplayName((String) body.get("displayName"));
        @SuppressWarnings("unchecked")
        List<String> screens = (List<String>) body.getOrDefault("screenIds", List.of());
        r.setScreenIds(String.join(",", screens));
        roleConfigs.save(r);
        Map<String,Object> res = new LinkedHashMap<>();
        res.put("roleName", r.getRoleName());
        return ResponseEntity.ok(res);
    }

    // ── Clients CRUD ─────────────────────────────────────────────────────────
    @PostMapping("/clients")
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> createClient(@RequestBody Map<String,Object> body) {
        com.orbit.domain.client.Client c = new com.orbit.domain.client.Client();
        c.setName((String) body.get("name"));
        c.setCode((String) body.getOrDefault("code",""));
        c.setContactName((String) body.get("contactName"));
        if (body.get("healthGreenThreshold") != null)
            c.setHealthGreenThreshold(Integer.parseInt(body.get("healthGreenThreshold").toString()));
        if (body.get("healthAmberThreshold") != null)
            c.setHealthAmberThreshold(Integer.parseInt(body.get("healthAmberThreshold").toString()));
        if (body.get("csatLaunch") != null)
            c.setCsatLaunch(new java.math.BigDecimal(body.get("csatLaunch").toString()));
        if (body.get("csatBau") != null)
            c.setCsatBau(new java.math.BigDecimal(body.get("csatBau").toString()));
        if (body.get("engagementScore") != null)
            c.setEngagementScore(Integer.parseInt(body.get("engagementScore").toString()));
        clients.save(c);
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("id", c.getId());
        return ResponseEntity.ok(r);
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> updateClient(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        return clients.findById(id).map(c -> {
            if (body.containsKey("name"))                c.setName((String) body.get("name"));
            if (body.containsKey("code"))                c.setCode((String) body.get("code"));
            if (body.containsKey("contactName"))         c.setContactName((String) body.get("contactName"));
            if (body.containsKey("healthGreenThreshold"))
                c.setHealthGreenThreshold(Integer.parseInt(body.get("healthGreenThreshold").toString()));
            if (body.containsKey("healthAmberThreshold"))
                c.setHealthAmberThreshold(Integer.parseInt(body.get("healthAmberThreshold").toString()));
            if (body.containsKey("active"))              c.setActive(Boolean.TRUE.equals(body.get("active")));
            // F1 — admin-entered CSAT (1–10, one decimal) + engagement (0–100)
            if (body.containsKey("csatLaunch"))
                c.setCsatLaunch(body.get("csatLaunch") == null ? null
                    : new java.math.BigDecimal(body.get("csatLaunch").toString()));
            if (body.containsKey("csatBau"))
                c.setCsatBau(body.get("csatBau") == null ? null
                    : new java.math.BigDecimal(body.get("csatBau").toString()));
            if (body.containsKey("engagementScore"))
                c.setEngagementScore(body.get("engagementScore") == null ? null
                    : Integer.parseInt(body.get("engagementScore").toString()));
            clients.save(c);
            return ResponseEntity.ok(Map.of("ok", true));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/clients/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> deleteClient(@PathVariable Long id) {
        clients.findById(id).ifPresent(c -> { c.setActive(false); clients.save(c); });
        return ResponseEntity.noContent().build();
    }

    // ── Single user role update ───────────────────────────────────────────────
    @PutMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        String role = body.get("role") == null ? null : body.get("role").toString().toUpperCase().replace(" ", "_");
        if (role == null || role.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "role required"));
        }
        if (roleConfigs.findByRoleName(role).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown role: " + role));
        }
        return users.findById(id).map(u -> {
            u.setRole(role);
            users.save(u);
            return ResponseEntity.ok(Map.of("ok", true, "id", id, "role", role));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Bulk user import ──────────────────────────────────────────────────────
    @PostMapping("/users/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> bulkImportUsers(@RequestBody List<Map<String,Object>> rows) {
        List<Map<String,Object>> results = new ArrayList<>();
        for (Map<String,Object> row : rows) {
            String email = (String) row.get("email");
            if (email == null || email.isBlank()) continue;
            boolean exists = users.findByEmail(email).isPresent();
            if (!exists) {
                // Each imported user gets a unique random temp password (no shared
                // constant). The plaintext is returned once so the admin can hand it
                // to the user, who resets it on first login.
                String tempPw = randomPassword();
                com.orbit.domain.client.AppUser u = new com.orbit.domain.client.AppUser();
                u.setName((String) row.getOrDefault("name", email));
                u.setEmail(email);
                u.setPassword(passwordEncoder.encode(tempPw));
                Object rawRole = row.get("role");
                String role = (rawRole == null ? "PM" : rawRole.toString()).toUpperCase().replace(" ","_");
                if (roleConfigs.findByRoleName(role).isEmpty()) {
                    results.add(Map.of("email", email, "status", "rejected_unknown_role", "role", role));
                    continue;
                }
                u.setRole(role);
                String name = u.getName();
                String[] parts = name.split(" ");
                String initials = parts.length >= 2
                    ? ("" + parts[0].charAt(0) + parts[parts.length-1].charAt(0)).toUpperCase()
                    : name.substring(0, Math.min(2, name.length())).toUpperCase();
                u.setInitials(initials);
                u.setAvatarColor("#6366F1");
                users.save(u);
                results.add(Map.of("email", email, "status", "created", "tempPassword", tempPw));
            } else {
                results.add(Map.of("email", email, "status", "skipped_exists"));
            }
        }
        return ResponseEntity.ok(Map.of("processed", results.size(), "results", results));
    }

    /** Cryptographically-random one-time password for provisioned accounts. */
    private static String randomPassword() {
        byte[] bytes = new byte[15];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ── Projects CRUD ─────────────────────────────────────────────────────────

    @GetMapping("/projects")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public List<Map<String,Object>> listProjects() {
        return projects.findByActiveTrue().stream().map(p -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",              p.getId());
            m.put("name",            p.getName());
            m.put("clientId",        p.getClient()    != null ? p.getClient().getId()    : null);
            m.put("clientName",      p.getClient()    != null ? p.getClient().getName()  : "");
            m.put("portfolioId",     p.getPortfolio() != null ? p.getPortfolio().getId() : null);
            m.put("portfolioName",   p.getPortfolio() != null ? p.getPortfolio().getName(): "");
            m.put("jiraProjectKeys", p.getJiraProjectKeys());
            m.put("jiraJqlOverride", p.getJiraJqlOverride());
            return m;
        }).collect(Collectors.toList());
    }

    @PostMapping("/projects")
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> createProject(@RequestBody Map<String,Object> body) {
        Project p = new Project();
        p.setName((String) body.get("name"));
        if (body.get("clientId") != null)
            clients.findById(Long.valueOf(body.get("clientId").toString())).ifPresent(p::setClient);
        if (body.get("portfolioId") != null)
            portfolios.findById(Long.valueOf(body.get("portfolioId").toString())).ifPresent(p::setPortfolio);
        if (body.get("jiraProjectKeys") != null) p.setJiraProjectKeys((String) body.get("jiraProjectKeys"));
        if (body.get("jiraJqlOverride")  != null) p.setJiraJqlOverride((String) body.get("jiraJqlOverride"));
        if (body.get("jiraCrFilter")     != null) p.setJiraCrFilter((String) body.get("jiraCrFilter"));
        if (body.get("jiraBugFilter")    != null) p.setJiraBugFilter((String) body.get("jiraBugFilter"));
        if (body.get("isSharedProdBugs") != null) p.setSharedProdBugs(Boolean.TRUE.equals(body.get("isSharedProdBugs")));
        if (body.get("clientCodeField")  != null) p.setClientCodeField((String) body.get("clientCodeField"));
        projects.save(p);
        return ResponseEntity.ok(Map.of("id", p.getId()));
    }

    @PutMapping("/projects/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> updateProject(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        return projects.findById(id).map(p -> {
            if (body.containsKey("name"))            p.setName((String) body.get("name"));
            if (body.containsKey("jiraProjectKeys")) p.setJiraProjectKeys((String) body.get("jiraProjectKeys"));
            if (body.containsKey("jiraJqlOverride")) p.setJiraJqlOverride((String) body.get("jiraJqlOverride"));
            if (body.containsKey("jiraCrFilter"))    p.setJiraCrFilter((String) body.get("jiraCrFilter"));
            if (body.containsKey("jiraBugFilter"))   p.setJiraBugFilter((String) body.get("jiraBugFilter"));
            if (body.containsKey("clientId") && body.get("clientId") != null)
                clients.findById(Long.valueOf(body.get("clientId").toString())).ifPresent(p::setClient);
            if (body.containsKey("portfolioId") && body.get("portfolioId") != null)
                portfolios.findById(Long.valueOf(body.get("portfolioId").toString())).ifPresent(p::setPortfolio);
            if (body.containsKey("isSharedProdBugs")) p.setSharedProdBugs(Boolean.TRUE.equals(body.get("isSharedProdBugs")));
            if (body.containsKey("clientCodeField"))  p.setClientCodeField((String) body.get("clientCodeField"));
            projects.save(p);
            return ResponseEntity.ok(Map.of("ok", true));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/projects/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> deleteProject(@PathVariable Long id) {
        projects.findById(id).ifPresent(p -> { p.setActive(false); projects.save(p); });
        return ResponseEntity.noContent().build();
    }

    // ── Jira user sync ────────────────────────────────────────────────────────
    @PostMapping("/users/sync-jira")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> syncUsersFromJira() {
        return ResponseEntity.ok(Map.of(
            "message", "Jira user sync is not yet configured. Set JIRA_BASE_URL and JIRA_API_TOKEN to enable.",
            "status",  "NOT_CONFIGURED"
        ));
    }

    // ── Team role labels (V93) — fixed keys, configurable display labels ─────

    private static final List<String> TEAM_ROLE_ORDER = List.of(
        "internal_pm", "internal_am", "internal_sol", "internal_em",
        "internal_tech_lead", "internal_qa_lead", "internal_support_mgr");

    @GetMapping("/team-role-labels")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> getTeamRoleLabels() {
        return teamRoleLabels.findAll().stream()
            .sorted(Comparator.comparingInt(l -> {
                int i = TEAM_ROLE_ORDER.indexOf(l.getRoleKey());
                return i < 0 ? TEAM_ROLE_ORDER.size() : i;
            }))
            .map(l -> {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("roleKey", l.getRoleKey());
                m.put("label", l.getLabel());
                return m;
            }).collect(Collectors.toList());
    }

    @PutMapping("/team-role-labels/{roleKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateTeamRoleLabel(@PathVariable String roleKey,
                                                  @RequestBody Map<String,Object> body) {
        String label = body.get("label") == null ? null : body.get("label").toString().trim();
        if (label == null || label.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "label required"));
        return teamRoleLabels.findById(roleKey).map(l -> {
            l.setLabel(label);
            teamRoleLabels.save(l);
            return ResponseEntity.ok((Object) Map.of("roleKey", roleKey, "label", label));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Health profiles ───────────────────────────────────────────────────────

    @GetMapping("/health-profiles")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> getHealthProfiles() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("stages",  ProjectHealthService.ALL_STAGES);
        res.put("metrics", ProjectHealthService.ALL_METRICS);
        res.put("weights", healthService.allWeightsGrouped());
        return res;
    }

    @PutMapping("/health-profiles/{stage}/{metric}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateHealthWeight(
            @PathVariable String stage,
            @PathVariable String metric,
            @RequestBody Map<String, Object> body) {
        if (!ProjectHealthService.ALL_STAGES.contains(stage))
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown stage: " + stage));
        if (!ProjectHealthService.ALL_METRICS.contains(metric))
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown metric: " + metric));
        int weight = body.get("weight") != null
            ? ((Number) body.get("weight")).intValue() : 0;
        java.math.BigDecimal sensitivity = body.get("sensitivity") != null
            ? new java.math.BigDecimal(body.get("sensitivity").toString()) : null;
        healthService.upsertWeight(stage, metric, weight, sensitivity);
        return ResponseEntity.ok(Map.of("stage", stage, "metric", metric, "weight", weight));
    }

    // ── Project stage override ────────────────────────────────────────────────

    @PatchMapping("/projects/{id}/stage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> setProjectStage(@PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        return projects.findById(id).map(p -> {
            String stage = (String) body.get("healthStage");
            p.setHealthStage(stage == null || stage.isBlank() ? null : stage);
            if (body.get("goLiveDate") != null)
                p.setGoLiveDate(java.time.LocalDate.parse(body.get("goLiveDate").toString()));
            projects.save(p);
            return ResponseEntity.ok(Map.of(
                "id",          p.getId(),
                "healthStage", p.getHealthStage(),
                "goLiveDate",  p.getGoLiveDate() != null ? p.getGoLiveDate().toString() : null
            ));
        }).orElse(ResponseEntity.notFound().build());
    }
}
