package com.orbit.controller;

import com.orbit.domain.config.JiraConfig;
import com.orbit.domain.config.JiraSyncRun;
import com.orbit.repository.JiraConfigRepository;
import com.orbit.repository.JiraSyncRunRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.service.sync.JiraSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/jira-sync")
public class JiraSyncController {

    private final JiraSyncRunRepository syncRuns;
    private final JiraConfigRepository jiraConfigs;
    private final ProjectRepository projects;
    private final JiraSyncService jiraSyncService;
    private final com.orbit.service.sync.ChangelogBackfillService changelogBackfill;

    public JiraSyncController(JiraSyncRunRepository syncRuns, JiraConfigRepository jiraConfigs,
                               ProjectRepository projects,
                               JiraSyncService jiraSyncService,
                               com.orbit.service.sync.ChangelogBackfillService changelogBackfill) {
        this.syncRuns = syncRuns;
        this.jiraConfigs = jiraConfigs;
        this.projects = projects;
        this.jiraSyncService = jiraSyncService;
        this.changelogBackfill = changelogBackfill;
    }

    // ── Changelog backfill (F3) ──────────────────────────────────────────────

    @PostMapping("/backfill-changelog")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> backfillChangelog(@RequestBody(required = false) Map<String, Object> body) {
        Long projectId = body != null && body.get("projectId") != null
            ? Long.valueOf(body.get("projectId").toString()) : null;
        return changelogBackfill.trigger(projectId);
    }

    @GetMapping("/backfill-status")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String, Object> backfillStatus() {
        return changelogBackfill.status();
    }

    // ── Sync trigger ──────────────────────────────────────────────────────────

    /**
     * POST /api/v1/jira-sync/trigger
     * Body: { "type": "delta"|"full", "projectId": 123 (optional) }
     */
    @PostMapping("/trigger")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String, Object> trigger(@RequestBody(required = false) Map<String, Object> body) {
        String type = body != null ? (String) body.getOrDefault("type", "delta") : "delta";
        Long projectId = body != null && body.get("projectId") != null
            ? Long.valueOf(body.get("projectId").toString()) : null;
        return jiraSyncService.trigger(type, projectId);
    }

    // ── Jira connection + webhook config ─────────────────────────────────────

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String, Object> getConfig() {
        JiraConfig cfg = jiraConfigs.findFirstByOrderByIdAsc().orElse(new JiraConfig());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("baseUrl",       cfg.getBaseUrl()      != null ? cfg.getBaseUrl()  : "");
        m.put("email",         cfg.getEmail()         != null ? cfg.getEmail()   : "");
        m.put("apiTokenSet",   cfg.getApiToken()      != null && !cfg.getApiToken().isBlank());
        m.put("slaField",      cfg.getSlaField() != null ? cfg.getSlaField() : "");
        m.put("storyPointsField", cfg.getStoryPointsField() != null ? cfg.getStoryPointsField() : "");
        m.put("sprintField",      cfg.getSprintField()      != null ? cfg.getSprintField()      : "");
        m.put("smField",          cfg.getSmField()          != null ? cfg.getSmField()          : "");
        m.put("pjmField",         cfg.getPjmField()         != null ? cfg.getPjmField()         : "");
        m.put("developerField",   cfg.getDeveloperField()   != null ? cfg.getDeveloperField()   : "");
        m.put("webhookSecret", mask(cfg.getWebhookSecret()));
        m.put("webhookUrl",    "https://orbit.internal/api/jira/webhook");
        m.put("webhookEvents", "issue:created, issue:updated, issue:deleted, comment:created");
        m.put("webhookValidation", "HMAC-SHA256 · X-Hub-Signature-256");
        m.put("webhookRetry",  "3 retries · exponential backoff");
        m.put("updatedAt",     cfg.getUpdatedAt());
        m.put("updatedBy",     cfg.getUpdatedBy());
        return m;
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> saveConfig(@RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        JiraConfig cfg = jiraConfigs.findFirstByOrderByIdAsc().orElse(new JiraConfig());

        if (body.containsKey("baseUrl")) {
            String baseUrl = (String) body.get("baseUrl");
            if (baseUrl != null && !baseUrl.isBlank()) {
                try {
                    com.orbit.integration.SafeUrl.validatePublicHttps(baseUrl);
                } catch (IllegalArgumentException e) {
                    throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "baseUrl rejected: " + e.getMessage());
                }
            }
            cfg.setBaseUrl(baseUrl);
        }
        if (body.containsKey("email"))
            cfg.setEmail((String) body.get("email"));

        // Only overwrite token/secret if a real (non-masked) value is provided
        String token = (String) body.get("apiToken");
        if (token != null && !token.isBlank() && !token.startsWith("•"))
            cfg.setApiToken(token);

        String secret = (String) body.get("webhookSecret");
        if (secret != null && !secret.isBlank() && !secret.startsWith("•"))
            cfg.setWebhookSecret(secret);

        if (body.containsKey("slaField"))
            cfg.setSlaField((String) body.get("slaField"));
        if (body.containsKey("storyPointsField"))
            cfg.setStoryPointsField((String) body.get("storyPointsField"));
        if (body.containsKey("sprintField"))
            cfg.setSprintField((String) body.get("sprintField"));
        if (body.containsKey("smField"))
            cfg.setSmField((String) body.get("smField"));
        if (body.containsKey("pjmField"))
            cfg.setPjmField((String) body.get("pjmField"));
        if (body.containsKey("developerField"))
            cfg.setDeveloperField((String) body.get("developerField"));

        cfg.setUpdatedAt(LocalDateTime.now());
        cfg.setUpdatedBy(request.getUserPrincipal() != null
            ? request.getUserPrincipal().getName() : "unknown");

        jiraConfigs.save(cfg);
        return Map.of("saved", true);
    }

    /** Kept for backwards-compat; delegates to getConfig() */
    @GetMapping("/webhook-config")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String, Object> webhookConfig() {
        return getConfig();
    }

    // ── Sync runs ─────────────────────────────────────────────────────────────

    /** Paged run history: {content, page, size, totalPages, totalElements}. */
    @GetMapping("/runs")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String, Object> syncRuns(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter fullFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Page<JiraSyncRun> result = syncRuns.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt")));

        // One lookup for the page's project names — no N+1
        List<Long> projectIds = result.getContent().stream()
            .map(JiraSyncRun::getProjectId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> projectNames = projectIds.isEmpty() ? Map.of()
            : projects.findAllById(projectIds).stream()
                .collect(Collectors.toMap(p -> p.getId(), p -> p.getName()));

        List<Map<String, Object>> content = result.getContent().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",           r.getId());
            m.put("time",         r.getStartedAt() != null ? r.getStartedAt().format(fmt) : "");
            m.put("startedAt",    r.getStartedAt() != null ? r.getStartedAt().format(fullFmt) : null);
            m.put("completedAt",  r.getCompletedAt() != null ? r.getCompletedAt().format(fullFmt) : null);
            m.put("type",         r.getSyncType());
            m.put("issues",       r.getIssuesProcessed() != null ? r.getIssuesProcessed() : 0);
            m.put("status",       r.getStatus());
            m.put("dur",          r.getDurationMs() != null ? (r.getDurationMs() / 1000.0) + "s" : "—");
            m.put("durationMs",   r.getDurationMs());
            m.put("errorMessage", r.getErrorMessage());
            m.put("projectId",    r.getProjectId());
            m.put("projectName",  r.getProjectId() != null ? projectNames.get(r.getProjectId()) : null);
            m.put("triggeredBy",  r.getTriggeredBy());
            // Live progress — null on historical rows and when the
            // approximate-count call failed (frontend falls back to `issues`).
            m.put("totalExpected",  r.getTotalExpected());
            m.put("processedSoFar", r.getProcessedSoFar());
            m.put("pending", r.getTotalExpected() != null
                ? Math.max(0, r.getTotalExpected()
                    - (r.getProcessedSoFar() != null ? r.getProcessedSoFar() : 0))
                : null);
            // Ordered scope + in-flight project; null on historical rows.
            m.put("projectScope", r.getProjectScope() != null
                ? Arrays.stream(r.getProjectScope().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList()
                : null);
            m.put("currentProject", r.getCurrentProject());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("content",       content);
        envelope.put("page",          page);
        envelope.put("size",          size);
        envelope.put("totalPages",    result.getTotalPages());
        envelope.put("totalElements", result.getTotalElements());
        return envelope;
    }

    /**
     * Real field-mapping rows (widget-parity plan F3 step 8 — replaces the
     * old hardcoded demo). Built-ins always present; the five configurable
     * custom-field mappings reflect jira_config, flagged when unmapped.
     * Keys (jf/to/it/notes/ok) match the Field-mapping table renderer.
     */
    @GetMapping("/field-mappings")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public List<Map<String, Object>> fieldMappings() {
        JiraConfig cfg = jiraConfigs.findFirstByOrderByIdAsc().orElse(new JiraConfig());
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(mappingRow("issuetype.name", "issueType", "All", "CR / UAT bug / Prod bug classification", true));
        rows.add(mappingRow("status.name", "lifecycleStage", "All", "Via lifecycle_mappings (Admin → Lifecycle)", true));
        rows.add(mappingRow("priority.name", "severity", "All", "P0–P3 convention", true));
        rows.add(mappingRow("assignee.displayName", "assigneeName", "All", "Owner donut (assignee view)", true));
        rows.add(mappingRow("fixVersions[0].name", "fixVersion", "All", "Release target", true));
        rows.add(mappingRow("changelog", "issue_transitions ledger", "All",
            "Webhook + backfill: status / sprint / story-point history", true));
        rows.add(mappingRow("reporter.displayName + emailAddress", "reporterName + reporterEmail", "All",
            "Standard reporter — who raised the issue", true));
        rows.add(configurable(cfg.getSlaField(), "slaStatus + slaRemainingHours", "Bugs", "JSM SLA field"));
        rows.add(configurable(cfg.getStoryPointsField(), "storyPoints", "All", "Velocity committed/delivered SP"));
        rows.add(configurable(cfg.getSprintField(), "sprints + sprint membership", "All", "Sprint tags, velocity, predictability"));
        rows.add(configurable(cfg.getSmField(), "smOwner", "CR", "Solutioning Manager donut"));
        rows.add(configurable(cfg.getPjmField(), "pjmOwner", "CR", "Project Manager (PjM) donut"));
        rows.add(configurable(cfg.getDeveloperField(), "developerName", "All", "Developer attribution (user picker)"));
        return rows;
    }

    private static Map<String, Object> mappingRow(String jf, String to, String it, String notes, boolean ok) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jf", jf);
        m.put("to", to);
        m.put("it", it);
        m.put("notes", notes);
        m.put("ok", ok);
        return m;
    }

    private static Map<String, Object> configurable(String fieldId, String to, String it, String purpose) {
        boolean mapped = fieldId != null && !fieldId.isBlank();
        return mappingRow(mapped ? fieldId : "—", to, it,
            mapped ? purpose : purpose + " — not mapped (set in Custom field mappings above)", mapped);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String, Object> stats() {
        var runs = syncRuns.findTop20ByOrderByStartedAtDesc();
        long success = runs.stream().filter(r -> "Success".equals(r.getStatus())).count();
        int total = runs.stream().mapToInt(r -> r.getIssuesProcessed() != null ? r.getIssuesProcessed() : 0).sum();
        return Map.of(
            "issuesSynced",    total,
            "issuesSyncedSub", "across all projects",
            "webhooksToday",   runs.stream().filter(r -> "Webhook".equals(r.getSyncType())).count(),
            "webhooksSub",     "events received",
            "deltaSyncs",      runs.stream().filter(r -> "Delta".equals(r.getSyncType())).count(),
            "deltaSyncsSub",   "incremental syncs",
            "lastFullSync",    runs.stream().filter(r -> "Full".equals(r.getSyncType())).findFirst()
                .map(r -> r.getStartedAt() != null
                    ? r.getStartedAt().format(DateTimeFormatter.ofPattern("MMM d HH:mm")) : "—")
                .orElse("Never"),
            "lastFullSyncSub", success + "/" + runs.size() + " runs succeeded"
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String mask(String val) {
        if (val == null || val.isBlank()) return "";
        if (val.length() <= 4) return "•".repeat(val.length());
        return val.substring(0, 2) + "•".repeat(Math.min(val.length() - 2, 10));
    }
}
