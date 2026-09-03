package com.orbit.service.sync;

import com.orbit.domain.config.JiraConfig;
import com.orbit.domain.config.JiraSyncRun;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.JiraConfigRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.JiraSyncRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Backfills issue changelogs into the issue_transitions ledger. Work queue =
 * issues with changelog_synced_at IS NULL (resumable per-issue cursor); one
 * paginated GET /rest/api/3/issue/{key}/changelog per issue; honors 429
 * Retry-After; records a JiraSyncRun (syncType "ChangelogBackfill") so the
 * existing sync-runs UI shows progress for free. F3 step 7.
 */
@Service
public class ChangelogBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ChangelogBackfillService.class);
    private static final int BATCH = 200;       // issues per run slice
    private static final int PAGE_SIZE = 100;   // changelog entries per request

    private final JiraConfigRepository jiraConfigs;
    private final JiraIssueRepository issues;
    private final JiraSyncRunRepository syncRuns;
    private final IssueTransitionService transitions;
    private final RestTemplate restTemplate = com.orbit.integration.OutboundHttp.restTemplate();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger processedThisRun = new AtomicInteger();
    private volatile String lastError;

    public ChangelogBackfillService(JiraConfigRepository jiraConfigs,
                                    JiraIssueRepository issues,
                                    JiraSyncRunRepository syncRuns,
                                    IssueTransitionService transitions) {
        this.jiraConfigs = jiraConfigs;
        this.issues = issues;
        this.syncRuns = syncRuns;
        this.transitions = transitions;
    }

    public Map<String, Object> status() {
        long pending = issues.countByChangelogSyncedAtIsNull();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("running", running.get());
        out.put("pendingIssues", pending);
        out.put("processedThisRun", processedThisRun.get());
        if (lastError != null) out.put("lastError", lastError);
        return out;
    }

    /** Kicks off an async backfill slice. Returns immediately. */
    public Map<String, Object> trigger(Long projectId) {
        if (!running.compareAndSet(false, true)) {
            return Map.of("started", false, "reason", "backfill already running");
        }
        processedThisRun.set(0);
        lastError = null;
        runAsync(projectId);
        return Map.of("started", true);
    }

    @Async
    protected void runAsync(Long projectId) {
        JiraSyncRun run = new JiraSyncRun();
        run.setSyncType("ChangelogBackfill");
        run.setStatus("Running");
        run.setStartedAt(LocalDateTime.now());
        run = syncRuns.save(run);
        int processed = 0;
        try {
            JiraConfig cfg = jiraConfigs.findFirstByOrderByIdAsc().orElse(null);
            if (cfg == null || cfg.getBaseUrl() == null || cfg.getApiToken() == null) {
                throw new IllegalStateException("Jira not configured");
            }
            String auth = "Basic " + Base64.getEncoder().encodeToString(
                ((cfg.getEmail() != null ? cfg.getEmail() : "") + ":" + cfg.getApiToken())
                    .getBytes(StandardCharsets.UTF_8));

            List<JiraIssue> queue = projectId == null
                ? issues.findByChangelogSyncedAtIsNull(PageRequest.of(0, BATCH))
                : issues.findByProjectIdAndChangelogSyncedAtIsNull(projectId, PageRequest.of(0, BATCH));
            for (JiraIssue issue : queue) {
                backfillIssue(cfg.getBaseUrl(), auth, issue);
                processed++;
                processedThisRun.set(processed);
            }
            run.setStatus("Success");
        } catch (Exception e) {
            log.error("ChangelogBackfillService: failed — {}", e.getMessage(), e);
            run.setStatus("Failed");
            run.setErrorMessage(e.getMessage());
            lastError = e.getMessage();
        } finally {
            run.setIssuesProcessed(processed);
            run.setCompletedAt(LocalDateTime.now());
            run.setDurationMs((int) ChronoUnit.MILLIS.between(run.getStartedAt(), run.getCompletedAt()));
            syncRuns.save(run);
            running.set(false);
        }
    }

    @SuppressWarnings("unchecked")
    private void backfillIssue(String baseUrl, String auth, JiraIssue issue) throws InterruptedException {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", auth);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);

        int startAt = 0;
        while (true) {
            String url = baseUrl + "/rest/api/3/issue/" + issue.getIssueKey()
                + "/changelog?startAt=" + startAt + "&maxResults=" + PAGE_SIZE;
            Map<String, Object> body;
            try {
                body = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
            } catch (HttpClientErrorException.TooManyRequests e) {
                long waitSec = parseRetryAfter(e);
                log.warn("ChangelogBackfillService: 429 — waiting {}s", waitSec);
                Thread.sleep(waitSec * 1000);
                continue;
            } catch (HttpClientErrorException.NotFound e) {
                break; // issue deleted in Jira — mark cursor and move on
            }
            if (body == null) break;
            List<Map<String, Object>> values = (List<Map<String, Object>>) body.get("values");
            if (values == null || values.isEmpty()) break;
            for (Map<String, Object> history : values) {
                transitions.record(issue, transitions.parseHistory(history));
            }
            startAt += values.size();
            Object total = body.get("total");
            if (!(total instanceof Number n) || startAt >= n.intValue()) break;
        }
        issue.setChangelogSyncedAt(LocalDateTime.now());
        issues.save(issue);
    }

    private static long parseRetryAfter(HttpClientErrorException e) {
        try {
            String h = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
            return h == null ? 10 : Math.max(1, Long.parseLong(h));
        } catch (NumberFormatException ex) {
            return 10;
        }
    }
}
