package com.orbit.service.sync;

import com.orbit.domain.config.JiraConfig;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.domain.issue.Sprint;
import com.orbit.domain.issue.SprintIssue;
import com.orbit.repository.JiraConfigRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.SprintIssueRepository;
import com.orbit.repository.SprintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Scheduled sprint upkeep (F3): refreshes active/future sprint metadata via
 * GET /rest/agile/1.0/sprint/{id} (by id — no Board API), and on an observed
 * future→active flip snapshots the members' story points into
 * committed_story_points (D4 committed semantics). Sprints that predate the
 * rollout never get a snapshot — committed_snapshot_at stays NULL and velocity
 * payloads flag them "approx".
 */
@Service
public class SprintSyncService {

    private static final Logger log = LoggerFactory.getLogger(SprintSyncService.class);
    private static final long GRACE_MINUTES = 15;

    private final JiraConfigRepository jiraConfigs;
    private final SprintRepository sprints;
    private final SprintIssueRepository memberships;
    private final JiraIssueRepository issues;
    private final RestTemplate restTemplate = com.orbit.integration.OutboundHttp.restTemplate();

    public SprintSyncService(JiraConfigRepository jiraConfigs,
                             SprintRepository sprints,
                             SprintIssueRepository memberships,
                             JiraIssueRepository issues) {
        this.jiraConfigs = jiraConfigs;
        this.sprints = sprints;
        this.memberships = memberships;
        this.issues = issues;
    }

    // @Transactional sits on the scheduled entrypoint: the internal
    // snapshotCommitted(sprint) call is a self-invocation that never crosses
    // the CGLIB proxy, so its own @Transactional is invisible on this path.
    @Scheduled(cron = "${orbit.jira.sprint-sync-cron:0 15 * * * *}")
    @Transactional
    public void refresh() {
        JiraConfig cfg = jiraConfigs.findFirstByOrderByIdAsc().orElse(null);
        if (cfg == null || cfg.getBaseUrl() == null || cfg.getApiToken() == null
                || cfg.getSprintField() == null || cfg.getSprintField().isBlank()) {
            return; // sprint sync dark until the Sprint field is mapped
        }
        String auth = "Basic " + Base64.getEncoder().encodeToString(
            ((cfg.getEmail() != null ? cfg.getEmail() : "") + ":" + cfg.getApiToken())
                .getBytes(StandardCharsets.UTF_8));

        for (Sprint sprint : sprints.findByStateIn(List.of("future", "active"))) {
            String before = sprint.getState();
            if (!refreshSprint(cfg.getBaseUrl(), auth, sprint)) continue;
            if (!"active".equals(before) && "active".equals(sprint.getState())) {
                snapshotCommitted(sprint);
            }
            if ("closed".equals(sprint.getState()) && sprint.getCompleteDate() == null) {
                sprint.setCompleteDate(LocalDateTime.now());
                sprints.save(sprint);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean refreshSprint(String baseUrl, String auth, Sprint sprint) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", auth);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        try {
            Map<String, Object> body = restTemplate.exchange(
                baseUrl + "/rest/agile/1.0/sprint/" + sprint.getJiraSprintId(),
                HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
            if (body == null) return false;
            if (body.get("state") instanceof String state) sprint.setState(state.toLowerCase());
            if (body.get("name") instanceof String name) sprint.setName(name);
            Object board = body.get("originBoardId");
            if (board instanceof Number b) sprint.setBoardId(b.longValue());
            LocalDateTime start = com.orbit.integration.jira.JiraDates.parse(body.get("startDate"));
            LocalDateTime end = com.orbit.integration.jira.JiraDates.parse(body.get("endDate"));
            LocalDateTime complete = com.orbit.integration.jira.JiraDates.parse(body.get("completeDate"));
            if (start != null) sprint.setStartDate(start);
            if (end != null) sprint.setEndDate(end);
            if (complete != null) sprint.setCompleteDate(complete);
            if (body.get("goal") instanceof String goal) sprint.setGoal(goal);
            sprint.setLastSyncedAt(LocalDateTime.now());
            sprints.save(sprint);
            return true;
        } catch (RestClientException e) {
            log.warn("SprintSyncService: sprint {} refresh failed — {}", sprint.getJiraSprintId(), e.getMessage());
            return false;
        }
    }

    /**
     * D4: on activation, members within the grace window are the commitment —
     * their current SP is frozen into committed_story_points.
     */
    @Transactional
    public void snapshotCommitted(Sprint sprint) {
        LocalDateTime cutoff = sprint.getStartDate() == null
            ? LocalDateTime.now() : sprint.getStartDate().plusMinutes(GRACE_MINUTES);
        for (SprintIssue si : memberships.findBySprintId(sprint.getId())) {
            boolean committed = si.getRemovedAt() == null
                && (si.getAddedAt() == null || !si.getAddedAt().isAfter(cutoff));
            si.setCommitted(committed);
            if (committed) {
                BigDecimal sp = issues.findById(si.getIssueId())
                    .map(JiraIssue::getStoryPoints).orElse(null);
                si.setCommittedStoryPoints(sp);
            }
            memberships.save(si);
        }
        sprint.setCommittedSnapshotAt(LocalDateTime.now());
        sprints.save(sprint);
        log.info("SprintSyncService: committed snapshot for sprint '{}' ({})",
            sprint.getName(), sprint.getJiraSprintId());
    }
}
