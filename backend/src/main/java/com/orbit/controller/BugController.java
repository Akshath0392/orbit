package com.orbit.controller;

import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.ProjectRepository;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/bugs")
public class BugController {

    private static final List<String> CLOSED_BUG = List.of("Closed","Invalid","Resolved","Canceled");

    private final JiraIssueRepository issues;
    private final ProjectRepository projects;
    // Sort param format: "field,asc" | "field,desc" — field is whitelisted to JiraIssue columns.
    private static final java.util.Map<String,String> SORTABLE = java.util.Map.of(
        "key",      "issueKey",
        "summary",  "summary",
        "sev",      "severity",
        "stage",    "lifecycleStage",
        "slaS",     "slaStatus",
        "rem",      "slaRemainingHours",
        "owner",    "assigneeName",
        "assignee", "assigneeName",
        "age",      "createdAt",
        "cycle",    "updatedAt"
    );
    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) return Sort.by(Sort.Direction.DESC, "updatedAt");
        String[] parts = sort.split(",");
        String field = SORTABLE.get(parts[0].trim());
        if (field == null) return Sort.by(Sort.Direction.DESC, "updatedAt");
        Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, field);
    }

    public BugController(JiraIssueRepository issues, ProjectRepository projects) {
        this.issues = issues; this.projects = projects;
    }

    @GetMapping("/prod")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Page<Map<String,Object>> prodBugs(
            @RequestParam(required=false) Long clientId,
            @RequestParam(required=false) String severity,
            @RequestParam(required=false) String slaStatus,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String sort) {
        return issues.findProdBugs(clientId, severity, slaStatus, PageRequest.of(page, size, parseSort(sort)))
            .map(i -> {
                long hoursAgo = i.getCreatedAt() != null
                    ? java.time.Duration.between(i.getCreatedAt(), java.time.LocalDateTime.now()).toHours() : 0;
                String age = hoursAgo < 24 ? hoursAgo + "h" : (hoursAgo/24) + "d";
                @SuppressWarnings("unchecked")
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("key", i.getIssueKey());
                row.put("summary", i.getSummary());
                row.put("sev", i.getSeverity());
                row.put("slaS", i.getSlaStatus() != null ? i.getSlaStatus() : "On track");
                row.put("client", i.getClient() != null ? i.getClient().getCode() : "");
                row.put("owner", i.getAssigneeName() != null ? i.getAssigneeName() : "—");
                row.put("age", age);
                row.put("rem", i.getSlaRemainingHours() != null ? i.getSlaRemainingHours() + "h" : "—");
                row.put("reopen", i.getReopenCount() != null && i.getReopenCount() > 0);
                if (i.getBertSuggestedSeverity() != null) {
                    row.put("bertSuggestion", Map.of(
                        "severity", i.getBertSuggestedSeverity(),
                        "owner", i.getBertSuggestedOwner() != null ? i.getBertSuggestedOwner() : "",
                        "accepted", i.getBertSuggestionAccepted() != null ? i.getBertSuggestionAccepted() : false
                    ));
                }
                return row;
            });
    }

    @GetMapping("/prod/summary")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String,Object> prodSummary(@RequestParam(required=false) Long clientId,
                                           @RequestParam(required=false) Long portfolioId) {
        return summary("PROD_BUG", clientId, portfolioId);
    }

    @GetMapping("/uat/summary")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String,Object> uatSummary(@RequestParam(required=false) Long clientId,
                                          @RequestParam(required=false) Long portfolioId) {
        return summary("UAT_BUG", clientId, portfolioId);
    }

    /**
     * Shared bug-summary aggregator. Returns the same shape for either issue type so
     * the same 5 frontend tiles work for both Production and UAT tabs:
     *   { p0Open, p1Open, p2Open, p3Open, slaBreached, slaAtRisk, reopened, unassigned }
     *
     * UAT bugs typically have null slaStatus so SLA tile counts are 0 — that's expected.
     */
    private Map<String,Object> summary(String type, Long clientId, Long portfolioId) {
        Map<String,Object> m = new LinkedHashMap<>();
        if (portfolioId != null) {
            List<Long> pids = projects.findByPortfolioIdAndActiveTrue(portfolioId)
                .stream().map(p -> p.getId()).collect(Collectors.toList());
            if (pids.isEmpty()) {
                m.put("p0Open",0); m.put("p1Open",0); m.put("p2Open",0); m.put("p3Open",0);
                m.put("slaBreached",0); m.put("slaAtRisk",0);
                m.put("reopened",0); m.put("unassigned",0);
                return m;
            }
            Map<String,Long> sevMap = new HashMap<>();
            var sevRows = "PROD_BUG".equals(type)
                ? issues.countOpenProdBugsBySeverityForProjects(pids)
                : issues.countOpenUatBugsBySeverityForProjects(pids);
            sevRows.forEach(r -> sevMap.put(String.valueOf(r[0]), ((Number) r[1]).longValue()));
            m.put("p0Open",      sevMap.getOrDefault("P0", 0L));
            m.put("p1Open",      sevMap.getOrDefault("P1", 0L));
            m.put("p2Open",      sevMap.getOrDefault("P2", 0L));
            m.put("p3Open",      sevMap.getOrDefault("P3", 0L));
            // SLA only meaningful for production bugs today
            m.put("slaBreached", "PROD_BUG".equals(type) ? issues.countOpenBugsBySlaStatusForProjects(pids, "Breached") : 0L);
            m.put("slaAtRisk",   "PROD_BUG".equals(type) ? issues.countOpenBugsBySlaStatusForProjects(pids, "At risk")  : 0L);
            m.put("reopened",   0);
            m.put("unassigned", 0);
            return m;
        }
        // Null-safe global / client-scoped path (works for both PROD_BUG and UAT_BUG)
        long p0       = issues.countOpenByClientTypeAndSeverityIn(clientId, type, List.of("P0"));
        long p1       = issues.countOpenByClientTypeAndSeverityIn(clientId, type, List.of("P1"));
        long p2       = issues.countOpenByClientTypeAndSeverityIn(clientId, type, List.of("P2"));
        long p3       = issues.countOpenByClientTypeAndSeverityIn(clientId, type, List.of("P3"));
        long breached = issues.countOpenBySlaStatusAndType(clientId, type, "Breached");
        long atRisk   = issues.countOpenBySlaStatusAndType(clientId, type, "At risk");
        long reopen   = issues.countOpenReopenedByClientAndType(clientId, type);
        long unassign = issues.countOpenUnassignedByClientAndType(clientId, type);
        m.put("p0Open", p0); m.put("p1Open", p1); m.put("p2Open", p2); m.put("p3Open", p3);
        m.put("slaBreached", breached); m.put("slaAtRisk", atRisk);
        m.put("reopened", reopen); m.put("unassigned", unassign);
        return m;
    }

    @GetMapping("/uat")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Page<Map<String,Object>> uatBugs(
            @RequestParam(required=false) Long clientId,
            @RequestParam(required=false) String stage,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String sort) {
        return issues.findUatBugs(clientId, stage, PageRequest.of(page, size, parseSort(sort)))
            .map(i -> {
                long hoursAgo = i.getCreatedAt() != null
                    ? java.time.Duration.between(i.getCreatedAt(), java.time.LocalDateTime.now()).toHours() : 0;
                String age = hoursAgo < 24 ? hoursAgo + "h" : (hoursAgo/24) + "d";
                Map<String,Object> uatRow = new LinkedHashMap<>();
                uatRow.put("key", i.getIssueKey());
                uatRow.put("summary", i.getSummary() != null ? i.getSummary() : "");
                uatRow.put("sev", i.getSeverity() != null ? i.getSeverity() : "Medium");
                uatRow.put("stage", i.getLifecycleStage() != null ? i.getLifecycleStage() : "Raised");
                uatRow.put("assignee", i.getAssigneeName() != null ? i.getAssigneeName() : "—");
                uatRow.put("cycle", 1);
                uatRow.put("age", age);
                uatRow.put("client", i.getClient() != null ? i.getClient().getName() : "");
                return uatRow;
            });
    }
}
