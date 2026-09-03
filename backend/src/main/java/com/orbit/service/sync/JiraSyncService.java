package com.orbit.service.sync;

import com.orbit.domain.client.Project;
import com.orbit.domain.config.JiraConfig;
import com.orbit.domain.config.JiraSyncRun;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.integration.jira.JiraDates;
import com.orbit.repository.*;
import com.orbit.service.SlaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pulls issues from Jira REST API using credentials stored in jira_config.
 * Called by POST /api/v1/jira-sync/trigger.
 */
@Service
public class JiraSyncService {

    private static final Logger log = LoggerFactory.getLogger(JiraSyncService.class);
    private static final int PAGE_SIZE = 50;

    private final JiraConfigRepository jiraConfigs;
    private final ProjectRepository projects;
    private final JiraIssueRepository jiraIssues;
    private final JiraSyncRunRepository syncRuns;
    private final LifecycleMappingRepository lifecycleMappings;
    private final SlaService slaService;
    private final ProdBugRoutingService prodBugRouting;
    private final SprintIngestService sprintIngest;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final RestTemplate restTemplate = com.orbit.integration.OutboundHttp.restTemplate();

    @Value("${orbit.prod-bug-routing.enabled:true}")
    private boolean prodBugRoutingEnabled;

    @Value("${orbit.jira.delta-sync-enabled:true}")
    private boolean deltaSyncEnabled;

    public JiraSyncService(JiraConfigRepository jiraConfigs,
                            ProjectRepository projects,
                            JiraIssueRepository jiraIssues,
                            JiraSyncRunRepository syncRuns,
                            LifecycleMappingRepository lifecycleMappings,
                            SlaService slaService,
                            ProdBugRoutingService prodBugRouting,
                            SprintIngestService sprintIngest,
                            org.springframework.context.ApplicationEventPublisher events) {
        this.jiraConfigs = jiraConfigs;
        this.projects = projects;
        this.jiraIssues = jiraIssues;
        this.syncRuns = syncRuns;
        this.lifecycleMappings = lifecycleMappings;
        this.slaService = slaService;
        this.prodBugRouting = prodBugRouting;
        this.sprintIngest = sprintIngest;
        this.events = events;
    }

    /**
     * Standing 30-min delta pull so humans aren't the scheduler. No-ops
     * silently while Jira is unconfigured (trigger() returns before creating
     * a run row), attributed with the reserved 'scheduler' value.
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "${orbit.jira.delta-sync-cron:0 */30 * * * *}")
    public void scheduledDeltaSync() {
        if (!deltaSyncEnabled) return;
        JiraSyncRun inFlight = syncRuns.findByStatus("Running").stream()
            .filter(r -> blocksScheduledDelta(r, LocalDateTime.now()))
            .findFirst().orElse(null);
        if (inFlight != null) {
            recordSkippedTick(inFlight);
            return;
        }
        Map<String, Object> result = trigger("delta", null, "scheduler");
        log.info("JiraSyncService: scheduled delta sync → {}", result);
    }

    /**
     * A scheduled tick never syncs concurrently with an in-flight Full/Delta
     * run (duplicate upserts + Jira API contention). Only issue-sync types
     * block. A Running row older than 2h is a crashed run's leftover: blocking
     * on it would silence the scheduler forever. Manual triggers stay
     * unguarded on purpose — forcing a sync is a deliberate act.
     */
    static boolean blocksScheduledDelta(JiraSyncRun run, LocalDateTime now) {
        return ("Full".equals(run.getSyncType()) || "Delta".equals(run.getSyncType()))
            && run.getStartedAt() != null
            && run.getStartedAt().isAfter(now.minusHours(2));
    }

    /** The skipped tick stays visible: a zero-duration run row naming the blocker. */
    private void recordSkippedTick(JiraSyncRun inFlight) {
        String progress = inFlight.getTotalExpected() != null
            ? String.format(" (%,d/%,d processed)",
                inFlight.getProcessedSoFar() == null ? 0 : inFlight.getProcessedSoFar(),
                inFlight.getTotalExpected())
            : "";
        String reason = "skipped — " + inFlight.getSyncType() + " sync run #"
            + inFlight.getId() + " in progress" + progress;
        JiraSyncRun skip = new JiraSyncRun();
        skip.setSyncType("Delta");
        skip.setStatus("Skipped");
        skip.setStartedAt(LocalDateTime.now());
        skip.setCompletedAt(skip.getStartedAt());
        skip.setDurationMs(0);
        skip.setIssuesProcessed(0);
        skip.setTriggeredBy("scheduler");
        skip.setErrorMessage(reason);
        syncRuns.save(skip);
        log.info("JiraSyncService: scheduled delta {}", reason);
    }

    public Map<String, Object> trigger(String type, Long projectId) {
        return trigger(type, projectId, null);
    }

    /** triggeredBy null = resolve from the SecurityContext (manual API calls). */
    public Map<String, Object> trigger(String type, Long projectId, String triggeredBy) {
        JiraConfig config = jiraConfigs.findFirstByOrderByIdAsc().orElse(null);

        if (config == null || isBlank(config.getBaseUrl()) || isBlank(config.getApiToken())) {
            return error("Jira not configured. Set base URL and API token in the Connection & Webhook tab.");
        }

        List<Project> toSync = resolveProjects(projectId);
        if (toSync.isEmpty()) {
            return error("No active projects with Jira project keys configured. Set keys in the Project config tab.");
        }

        String basicAuth = buildBasicAuth(config.getEmail(), config.getApiToken());
        Map<String, String> stageMap = buildStageMap();

        JiraSyncRun run = startRun(type, projectId, triggeredBy);
        // Stamp the ordered scope up front; currentProject advances with the
        // loop below so pollers see which project the run is on.
        run.setProjectScope(toSync.stream().map(Project::getName).collect(Collectors.joining(", ")));
        run = syncRuns.save(run);
        recordExpectedTotal(config, basicAuth, toSync, type, run);

        int totalProcessed = 0;
        String errorMsg = null;

        try {
            for (Project project : toSync) {
                run.setCurrentProject(project.getName());
                run = syncRuns.save(run);
                String jql = buildJql(project, type);
                log.info("JiraSyncService: syncing project='{}' jql='{}'", project.getName(), jql);
                int synced = fetchAndUpsert(config, basicAuth, jql, project, stageMap, run);
                totalProcessed += synced;
                log.info("JiraSyncService: project='{}' synced {} issues", project.getName(), synced);
            }
            run.setStatus("Success");
            // Kept on Failed so the row shows which project the run died on.
            run.setCurrentProject(null);
        } catch (RestClientException e) {
            log.error("JiraSyncService: HTTP error — {}", e.getMessage());
            run.setStatus("Failed");
            errorMsg = "Jira API error: " + e.getMessage();
        } catch (Exception e) {
            log.error("JiraSyncService: unexpected error — {}", e.getMessage(), e);
            run.setStatus("Failed");
            errorMsg = e.getMessage();
        }

        run.setIssuesProcessed(totalProcessed);
        run.setCompletedAt(LocalDateTime.now());
        run.setDurationMs((int) ChronoUnit.MILLIS.between(run.getStartedAt(), run.getCompletedAt()));
        if (errorMsg != null) run.setErrorMessage(errorMsg);
        syncRuns.save(run);

        // Evict dashboard caches as soon as fresh Jira data lands.
        if ("Success".equals(run.getStatus())) events.publishEvent(new JiraDataChangedEvent("sync"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", run.getStatus());
        result.put("type", run.getSyncType());
        result.put("issuesProcessed", totalProcessed);
        result.put("durationMs", run.getDurationMs());
        if (errorMsg != null) result.put("error", errorMsg);
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Project> resolveProjects(Long projectId) {
        List<Project> all = projectId != null
            ? projects.findById(projectId)
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .map(List::of).orElse(List.of())
            : projects.findByActiveTrue();

        return all.stream()
            .filter(p -> !isBlank(p.getJiraProjectKeys()))
            .collect(Collectors.toList());
    }

    private String buildJql(Project project, String type) {
        if (!isBlank(project.getJiraJqlOverride())) return project.getJiraJqlOverride();

        String keys = Arrays.stream(project.getJiraProjectKeys().split(","))
            .map(String::trim).filter(k -> !k.isBlank())
            .collect(Collectors.joining(", "));

        StringBuilder jql = new StringBuilder("project in (").append(keys).append(")");

        List<String> typeClauses = new ArrayList<>();
        if (!isBlank(project.getJiraCrFilter()))  typeClauses.add("(" + project.getJiraCrFilter().trim() + ")");
        if (!isBlank(project.getJiraBugFilter())) typeClauses.add("(" + project.getJiraBugFilter().trim() + ")");
        if (!typeClauses.isEmpty()) {
            jql.append(" AND (").append(String.join(" OR ", typeClauses)).append(")");
        }

        if ("delta".equalsIgnoreCase(type)) {
            jql.append(" AND updated >= -1d ORDER BY updated DESC");
        } else {
            jql.append(" ORDER BY created DESC");
        }
        return jql.toString();
    }

    // Every configured jira_config mapping MUST be listed here: /search/jql only
    // returns requested fields, so an omission silently nulls the mapped column
    // on every JQL-synced issue while the field mapper itself stays correct.
    // Package-private for testing.
    List<String> requestedFields(JiraConfig cfg, Project project) {
        List<String> fields = new ArrayList<>(List.of(
            "summary", "status", "issuetype", "priority",
            "assignee", "reporter", "fixVersions", "created", "updated", "resolutiondate"
        ));
        for (String mapped : new String[]{cfg.getSlaField(), cfg.getStoryPointsField(),
                cfg.getSprintField(), cfg.getSmField(), cfg.getPjmField(),
                cfg.getDeveloperField()}) {
            if (!isBlank(mapped)) fields.add(mapped);
        }
        if (isRouting(project) && !isBlank(project.getClientCodeField())) {
            fields.add(project.getClientCodeField());
        }
        return fields;
    }

    /**
     * Sum the approximate-count of every project's JQL scope up front so the
     * runs page can show processed-vs-pending while the sync is still paging.
     * Best-effort — a count failure leaves totalExpected null (progress shows
     * as indeterminate) and never aborts the sync itself. ORDER BY is stripped
     * because /search/approximate-count rejects it.
     */
    private void recordExpectedTotal(JiraConfig cfg, String auth, List<Project> toSync,
                                     String type, JiraSyncRun run) {
        try {
            long expected = 0;
            for (Project project : toSync) {
                expected += approximateCount(cfg, auth, withoutOrderBy(buildJql(project, type)));
            }
            run.setTotalExpected((int) Math.min(expected, Integer.MAX_VALUE));
            syncRuns.save(run);
        } catch (Exception e) {
            log.warn("JiraSyncService: could not resolve expected total — {}", e.getMessage());
        }
    }

    /** approximate-count accepts only the filter part of a JQL query. */
    static String withoutOrderBy(String jql) {
        return jql == null ? null : jql.replaceAll("(?i)\\s+ORDER\\s+BY\\s+.*$", "");
    }

    private long approximateCount(JiraConfig cfg, String auth, String jql) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", auth);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = restTemplate.exchange(
            cfg.getBaseUrl() + "/rest/api/3/search/approximate-count",
            HttpMethod.POST, new HttpEntity<>(Map.of("jql", jql), headers), Map.class).getBody();
        Object c = body == null ? null : body.get("count");
        // Missing count on a 200 is treated as 0 — a false alarm beats silence here.
        return c instanceof Number n ? n.longValue() : 0L;
    }

    @SuppressWarnings("unchecked")
    private int fetchAndUpsert(JiraConfig cfg, String auth, String jql,
                                Project project, Map<String, String> stageMap, JiraSyncRun run) {
        // POST /rest/api/3/search/jql uses cursor-based pagination via nextPageToken
        String url = cfg.getBaseUrl() + "/rest/api/3/search/jql";
        String nextPageToken = null;
        int total = 0;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", auth);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

        // Requested fields are constant per project — built once, not per page.
        List<String> fields = requestedFields(cfg, project);

        do {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("jql", jql);
            requestBody.put("maxResults", PAGE_SIZE);
            requestBody.put("fields", fields);
            if (nextPageToken != null) requestBody.put("nextPageToken", nextPageToken);

            log.debug("JiraSyncService: POST {} requestBody={}", url, requestBody);
            Map<String, Object> body = restTemplate
                .exchange(url, HttpMethod.POST, new HttpEntity<>(requestBody, headers), Map.class)
                .getBody();
            if (body == null) { log.debug("JiraSyncService: response body was null"); break; }

            List<Map<String, Object>> issues = (List<Map<String, Object>>) body.get("issues");
            log.debug("JiraSyncService: response — issues={}, nextPageToken={}, warnings={}",
                issues == null ? 0 : issues.size(), body.get("nextPageToken"), body.get("warningMessages"));
            if (issues == null || issues.isEmpty()) break;

            for (Map<String, Object> raw : issues) {
                upsertIssue(raw, project, stageMap, cfg);
                total++;
            }

            // Flush progress after every page — no wrapping transaction, so
            // the commit is immediately visible to GET /jira-sync/runs pollers.
            run.setProcessedSoFar((run.getProcessedSoFar() == null ? 0 : run.getProcessedSoFar())
                + issues.size());
            syncRuns.save(run);

            nextPageToken = (String) body.get("nextPageToken");
            if (nextPageToken == null || issues.size() < PAGE_SIZE) break;
        } while (true);

        return total;
    }

    @SuppressWarnings("unchecked")
    private void upsertIssue(Map<String, Object> raw, Project project, Map<String, String> stageMap, JiraConfig cfg) {
        String key = (String) raw.get("key");
        if (isBlank(key)) return;

        JiraIssue issue = jiraIssues.findByIssueKey(key).orElse(new JiraIssue());

        issue.setIssueKey(key);
        issue.setProject(project);
        issue.setClient(project.getClient());

        Map<String, Object> fields = (Map<String, Object>) raw.get("fields");
        if (fields != null) {
            issue.setSummary((String) fields.get("summary"));

            Map<String, Object> issuetype = (Map<String, Object>) fields.get("issuetype");
            if (issuetype != null) issue.setIssueType(mapIssueType((String) issuetype.get("name")));

            // Shared prod-bug pool: every issue is a production bug and its client
            // is decided by a per-project custom field, not the project's own client.
            if (isRouting(project)) {
                issue.setIssueType("PROD_BUG");
                Object rawCode = fields.get(project.getClientCodeField());
                prodBugRouting.route(issue, rawCode, project);
            }

            Map<String, Object> status = (Map<String, Object>) fields.get("status");
            if (status != null) {
                String statusName = (String) status.get("name");
                issue.setJiraStatus(statusName);
                issue.setLifecycleStage(stageFor(stageMap, issue.getIssueType(), statusName));
            }

            Map<String, Object> priority = (Map<String, Object>) fields.get("priority");
            if (priority != null) {
                issue.setPriority((String) priority.get("name"));
                issue.setSeverity(mapPriority((String) priority.get("name")));
            }

            Map<String, Object> assignee = (Map<String, Object>) fields.get("assignee");
            if (assignee != null) issue.setAssigneeName((String) assignee.get("displayName"));

            // Mapped custom fields (story points / Sprint / SM / PjM / Developer)
            // plus the standard reporter — shared with webhook
            com.orbit.integration.jira.JiraFieldMapper.apply(issue, fields, cfg);

            // Jira's own timestamps — sync time goes to lastSyncedAt, never here.
            // created/updated are set before applySla so computed SLA uses real age;
            // resolutiondate is null while unresolved, which also clears it on reopen.
            LocalDateTime jiraCreated = JiraDates.parse(fields.get("created"));
            LocalDateTime jiraUpdated = JiraDates.parse(fields.get("updated"));
            if (jiraCreated != null) issue.setCreatedAt(jiraCreated);
            if (jiraUpdated != null) issue.setUpdatedAt(jiraUpdated);
            issue.setResolvedAt(JiraDates.parse(fields.get("resolutiondate")));

            // SLA: try Jira custom field (JSM), fall back to computed rules
            String slaFieldName = cfg != null ? cfg.getSlaField() : null;
            boolean slaSetFromJira = false;
            if (!isBlank(slaFieldName)) {
                Object slaFieldVal = fields.get(slaFieldName);
                String jiraSlaStatus = slaService.parseJiraSlaStatus(slaFieldVal);
                if (jiraSlaStatus != null) {
                    issue.setSlaStatus(jiraSlaStatus);
                    issue.setSlaRemainingHours(slaService.parseJiraSlaRemaining(slaFieldVal));
                    slaSetFromJira = true;
                }
            }
            if (!slaSetFromJira && ("PROD_BUG".equals(issue.getIssueType()) || "UAT_BUG".equals(issue.getIssueType()))) {
                slaService.applySla(issue);
            }
        }

        if (issue.getCreatedAt() == null) issue.setCreatedAt(LocalDateTime.now());
        if (issue.getUpdatedAt() == null) issue.setUpdatedAt(LocalDateTime.now());
        issue.setLastSyncedAt(LocalDateTime.now());
        jiraIssues.save(issue);

        // Sprint upsert + membership needs the persisted issue id (F3)
        if (fields != null && cfg != null && !isBlank(cfg.getSprintField())) {
            sprintIngest.ingestFieldValue(issue, fields.get(cfg.getSprintField()));
        }
    }

    private JiraSyncRun startRun(String type, Long projectId, String triggeredBy) {
        JiraSyncRun run = new JiraSyncRun();
        run.setSyncType("delta".equalsIgnoreCase(type) ? "Delta" : "Full");
        run.setStatus("Running");
        run.setStartedAt(LocalDateTime.now());
        run.setProjectId(projectId);
        run.setTriggeredBy(triggeredBy != null ? triggeredBy : currentUserEmail());
        return syncRuns.save(run);
    }

    // All trigger() callers run on the request thread; async workers must
    // capture this before dispatch (async threads have an empty context).
    static String currentUserEmail() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication();
        return auth != null && auth.getName() != null && !"anonymousUser".equals(auth.getName())
            ? auth.getName() : "system";
    }

    private Map<String, String> buildStageMap() {
        return buildStageMap(lifecycleMappings.findAll());
    }

    // Dual-keyed "issueType|status" with a bare-status fallback — the same Jira
    // status can map to different gauge stages per issue type; the old
    // status-only map was last-write-wins, mislabelling whichever type lost the
    // tie. ALL rows are the explicit wildcard: they own the bare-status
    // fallback outright, so a per-type row can no longer shadow an ALL row for
    // the other types by winning the insertion-order race.
    public static Map<String, String> buildStageMap(List<com.orbit.domain.client.LifecycleMapping> mappings) {
        Map<String, String> map = new HashMap<>();
        for (var m : mappings) {
            if ("ALL".equals(m.getIssueType())) map.put(m.getJiraStatus(), m.getGaugeStage());
        }
        for (var m : mappings) {
            if ("ALL".equals(m.getIssueType())) continue;
            map.put(m.getIssueType() + "|" + m.getJiraStatus(), m.getGaugeStage());
            map.putIfAbsent(m.getJiraStatus(), m.getGaugeStage());
        }
        return map;
    }

    /** Type-specific mapping first, bare-status fallback, raw status last. */
    public static String stageFor(Map<String, String> stageMap, String issueType, String status) {
        String s = stageMap.get(issueType + "|" + status);
        return s != null ? s : stageMap.getOrDefault(status, status);
    }

    private String buildBasicAuth(String email, String apiToken) {
        String credentials = (email != null ? email : "") + ":" + apiToken;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    // Package-private for testing
    String mapIssueType(String jiraType) {
        if (jiraType == null) return "CR";
        String t = jiraType.toLowerCase().trim();
        // Production bugs are only the explicitly tagged ones; default "bug" goes to UAT.
        if (t.equals("production bug") || t.equals("prod bug") || t.equals("production defect")) return "PROD_BUG";
        if (t.equals("bug") || t.equals("uat bug") || t.equals("defect") || t.equals("uat defect")) return "UAT_BUG";
        return "CR";
    }

    private String mapPriority(String p) {
        if (p == null) return "P2";
        return switch (p.toLowerCase()) {
            case "critical", "blocker" -> "P0";
            case "high", "major"       -> "P1";
            case "low", "minor", "trivial" -> "P3";
            default -> "P2";
        };
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    private boolean isRouting(Project p) {
        return prodBugRoutingEnabled && p.isSharedProdBugs() && !isBlank(p.getClientCodeField());
    }

    private Map<String, Object> error(String msg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "NOT_CONFIGURED");
        r.put("error", msg);
        return r;
    }
}
