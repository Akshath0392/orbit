package com.orbit.controller;

import com.orbit.domain.client.Client;
import com.orbit.domain.client.Project;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.domain.routing.ProdBugQuarantine;
import com.orbit.repository.ClientRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.ProdBugQuarantineRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.service.sync.ProdBugBackfillService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin surface for the shared prod-bug routing feature. Phase A ships the
 * read endpoints + config PUT; Phase C adds resolve + backfill. See
 * docs/plan/prod-bug-routing-plan.md.
 */
@RestController
@RequestMapping("/api/v1/admin/prod-bug-routing")
public class ProdBugRoutingController {

    private final ProjectRepository projects;
    private final ClientRepository clients;
    private final ProdBugQuarantineRepository quarantine;
    private final JiraIssueRepository jiraIssues;
    private final ProdBugBackfillService backfillService;

    public ProdBugRoutingController(ProjectRepository projects,
                                    ClientRepository clients,
                                    ProdBugQuarantineRepository quarantine,
                                    JiraIssueRepository jiraIssues,
                                    ProdBugBackfillService backfillService) {
        this.projects = projects;
        this.clients = clients;
        this.quarantine = quarantine;
        this.jiraIssues = jiraIssues;
        this.backfillService = backfillService;
    }

    // ── Shared-pool config ──────────────────────────────────────────────

    @GetMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> listConfig() {
        long openQuarantine = quarantine.countOpen();
        return projects.findBySharedProdBugsTrue().stream()
            .map(p -> row(p, openQuarantine))
            .toList();
    }

    @PutMapping("/config/{projectId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateConfig(@PathVariable Long projectId,
                                          @RequestBody Map<String, Object> body) {
        Project p = projects.findById(projectId).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();

        boolean shared = Boolean.TRUE.equals(body.get("isSharedProdBugs"));
        String field = trimOrNull(body.get("clientCodeField"));

        if (shared && (field == null || field.isBlank())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "clientCodeField is required when isSharedProdBugs=true"));
        }

        p.setSharedProdBugs(shared);
        p.setClientCodeField(shared ? field : null);
        projects.save(p);

        return ResponseEntity.ok(row(p, quarantine.countOpen()));
    }

    // ── Client-code assignments ─────────────────────────────────────────

    @GetMapping("/clients")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> listClientCodes() {
        return clients.findAll().stream()
            .map(c -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("clientId", c.getId());
                r.put("clientName", c.getName());
                r.put("code", c.getCode());
                r.put("hasCode", c.getCode() != null && !c.getCode().isBlank());
                return r;
            })
            .toList();
    }

    // ── Quarantine ─────────────────────────────────────────────────────

    @GetMapping("/quarantine")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> listQuarantine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<ProdBugQuarantine> pageResult = quarantine.findOpen(PageRequest.of(page, Math.min(size, 200)));
        List<Map<String, Object>> content = pageResult.getContent().stream()
            .map(q -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", q.getId());
                r.put("jiraKey", q.getJiraKey());
                r.put("jiraSummary", q.getJiraIssue() == null ? null : q.getJiraIssue().getSummary());
                r.put("rawClientCode", q.getRawClientCode());
                r.put("reason", q.getReason().name());
                r.put("seenAt", q.getSeenAt());
                r.put("lastSeenAt", q.getLastSeenAt());
                return r;
            }).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", content);
        out.put("totalElements", pageResult.getTotalElements());
        out.put("totalPages", pageResult.getTotalPages());
        out.put("page", page);
        out.put("size", size);
        return out;
    }

    @PostMapping("/clients/{clientId}/code")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> setClientCode(@PathVariable Long clientId,
                                            @RequestBody Map<String, Object> body) {
        Client c = clients.findById(clientId).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();

        String code = trimOrNull(body.get("code"));
        if (code == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "code is required and must be non-blank"));
        }
        String normalised = code.toUpperCase();

        // Enforce the partial unique index invariant at the app layer with a
        // clear error message — hitting a DB constraint later is ugly for users.
        Optional<Client> existing = clients.findByCodeIgnoreCase(normalised);
        if (existing.isPresent() && !existing.get().getId().equals(clientId)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "code " + normalised + " is already used by client " + existing.get().getName()));
        }

        c.setCode(normalised);
        clients.save(c);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("clientId", c.getId());
        r.put("clientName", c.getName());
        r.put("code", c.getCode());
        r.put("hasCode", true);
        return ResponseEntity.ok(r);
    }

    // ── Quarantine resolution ──────────────────────────────────────────

    @PostMapping("/quarantine/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resolveQuarantine(@PathVariable Long id,
                                                @RequestBody Map<String, Object> body) {
        ProdBugQuarantine q = quarantine.findById(id).orElse(null);
        if (q == null) return ResponseEntity.notFound().build();
        if (q.getResolvedAt() != null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "already resolved at " + q.getResolvedAt()));
        }

        String note = trimOrNull(body.get("note"));
        String assignCode = trimOrNull(body.get("assignClientCode"));

        if (assignCode != null) {
            Optional<Client> match = clients.findActiveByCodeIgnoreCase(assignCode);
            if (match.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "no active client with code " + assignCode));
            }
            JiraIssue issue = q.getJiraIssue();
            if (issue != null) {
                issue.setClient(match.get());
                issue.setUpdatedAt(LocalDateTime.now());
                jiraIssues.save(issue);
            }
        }

        q.setResolvedAt(LocalDateTime.now());
        q.setResolvedBy(currentUsername());
        q.setResolutionNote(note);
        quarantine.save(q);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", q.getId());
        r.put("jiraKey", q.getJiraKey());
        r.put("resolvedAt", q.getResolvedAt());
        r.put("resolvedBy", q.getResolvedBy());
        r.put("resolutionNote", q.getResolutionNote());
        r.put("assignedClientCode", assignCode);
        return ResponseEntity.ok(r);
    }

    // ── Historical backfill ────────────────────────────────────────────

    @PostMapping("/backfill/{projectId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> backfill(@PathVariable Long projectId) {
        Map<String, Object> result = backfillService.backfill(projectId);
        String status = (String) result.get("status");
        if ("NOT_FOUND".equals(status)) return ResponseEntity.status(404).body(result);
        if ("REJECTED".equals(status)) return ResponseEntity.badRequest().body(result);
        return ResponseEntity.ok(result);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private Map<String, Object> row(Project p, long openQuarantine) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("projectId", p.getId());
        r.put("projectName", p.getName());
        r.put("isSharedProdBugs", p.isSharedProdBugs());
        r.put("clientCodeField", p.getClientCodeField());
        r.put("quarantinedOpen", openQuarantine);
        return r;
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "admin";
    }

    private static String trimOrNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
