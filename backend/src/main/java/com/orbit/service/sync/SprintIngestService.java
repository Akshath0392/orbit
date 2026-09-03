package com.orbit.service.sync;

import com.orbit.domain.issue.JiraIssue;
import com.orbit.domain.issue.Sprint;
import com.orbit.domain.issue.SprintIssue;
import com.orbit.repository.SprintIssueRepository;
import com.orbit.repository.SprintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Upserts sprints and sprint membership. Sprint metadata comes from the issue
 * Sprint custom-field payload (Jira Cloud sends full sprint objects — no Board
 * API needed, F3 design); membership add/remove times come from Sprint-field
 * changelog diffs (comma-separated sprint-id lists). Shared by the JQL sync,
 * webhook and changelog backfill.
 */
@Service
public class SprintIngestService {

    private static final Logger log = LoggerFactory.getLogger(SprintIngestService.class);

    private final SprintRepository sprints;
    private final SprintIssueRepository memberships;

    public SprintIngestService(SprintRepository sprints, SprintIssueRepository memberships) {
        this.sprints = sprints;
        this.memberships = memberships;
    }

    /**
     * Upserts sprints from the issue's Sprint field value (list of sprint
     * objects) and ensures a membership row exists for each. addedAt stays
     * NULL here — only changelog diffs know when the issue joined.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public void ingestFieldValue(JiraIssue issue, Object sprintFieldValue) {
        if (!(sprintFieldValue instanceof List<?> list) || issue.getId() == null) return;
        Set<Long> currentIds = new LinkedHashSet<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, Object> raw = (Map<String, Object>) m;
            Object idRaw = raw.get("id");
            if (!(idRaw instanceof Number n)) continue;
            Sprint sprint = upsertSprint(n.longValue(), raw);
            currentIds.add(sprint.getJiraSprintId());
            ensureMembership(sprint, issue.getId(), null);
        }
        // Membership rows for sprints the issue is no longer in get closed by
        // changelog diffs; the field value alone can't date the removal.
        if (!currentIds.isEmpty()) {
            for (SprintIssue si : memberships.findByIssueId(issue.getId())) {
                Sprint s = sprints.findById(si.getSprintId()).orElse(null);
                if (s != null && !currentIds.contains(s.getJiraSprintId())
                        && si.getRemovedAt() == null && si.getAddedAt() == null) {
                    // unknown-history membership no longer current — close it undated
                    si.setRemovedAt(LocalDateTime.now());
                    memberships.save(si);
                }
            }
        }
    }

    @Transactional
    public Sprint upsertSprint(long jiraSprintId, Map<String, Object> raw) {
        Sprint sprint = sprints.findByJiraSprintId(jiraSprintId).orElseGet(() -> {
            Sprint s = new Sprint();
            s.setJiraSprintId(jiraSprintId);
            return s;
        });
        if (raw != null) {
            if (raw.get("name") instanceof String name) sprint.setName(name);
            if (raw.get("state") instanceof String state) sprint.setState(state.toLowerCase());
            Object board = raw.containsKey("boardId") ? raw.get("boardId") : raw.get("originBoardId");
            if (board instanceof Number b) sprint.setBoardId(b.longValue());
            LocalDateTime start = com.orbit.integration.jira.JiraDates.parse(raw.get("startDate"));
            LocalDateTime end = com.orbit.integration.jira.JiraDates.parse(raw.get("endDate"));
            LocalDateTime complete = com.orbit.integration.jira.JiraDates.parse(raw.get("completeDate"));
            if (start != null) sprint.setStartDate(start);
            if (end != null) sprint.setEndDate(end);
            if (complete != null) sprint.setCompleteDate(complete);
            if (raw.get("goal") instanceof String goal) sprint.setGoal(goal);
        }
        sprint.setLastSyncedAt(LocalDateTime.now());
        return sprints.save(sprint);
    }

    /**
     * Applies one Sprint-field changelog diff (from/to comma-separated
     * sprint-id lists) to the membership table — added ids get addedAt,
     * removed ids get removedAt. Replay-safe: earliest add / latest remove win.
     */
    @Transactional
    public void applyMembershipDiff(JiraIssue issue, String fromIds, String toIds, LocalDateTime at) {
        if (issue.getId() == null) return;
        Set<Long> before = parseIds(fromIds);
        Set<Long> after = parseIds(toIds);

        for (Long added : diff(after, before)) {
            Sprint sprint = sprints.findByJiraSprintId(added)
                .orElseGet(() -> upsertSprint(added, null)); // stub until metadata arrives
            SprintIssue si = ensureMembership(sprint, issue.getId(), at);
            if (si.getAddedAt() == null || (at != null && at.isBefore(si.getAddedAt()))) {
                si.setAddedAt(at);
            }
            if (si.getRemovedAt() != null && at != null && at.isAfter(si.getRemovedAt())) {
                si.setRemovedAt(null); // re-added after a removal
            }
            memberships.save(si);
        }
        for (Long removed : diff(before, after)) {
            sprints.findByJiraSprintId(removed).ifPresent(sprint ->
                memberships.findBySprintIdAndIssueId(sprint.getId(), issue.getId()).ifPresent(si -> {
                    if (si.getRemovedAt() == null || (at != null && at.isAfter(si.getRemovedAt()))) {
                        si.setRemovedAt(at);
                        memberships.save(si);
                    }
                }));
        }
    }

    private SprintIssue ensureMembership(Sprint sprint, Long issueId, LocalDateTime addedAt) {
        return memberships.findBySprintIdAndIssueId(sprint.getId(), issueId).orElseGet(() -> {
            SprintIssue si = new SprintIssue();
            si.setSprintId(sprint.getId());
            si.setIssueId(issueId);
            si.setAddedAt(addedAt);
            return memberships.save(si);
        });
    }

    private static Set<Long> parseIds(String csv) {
        Set<Long> out = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) return out;
        for (String part : csv.split(",")) {
            try {
                out.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException e) {
                log.debug("SprintIngestService: unparseable sprint id '{}'", part);
            }
        }
        return out;
    }

    private static Set<Long> diff(Set<Long> a, Set<Long> b) {
        Set<Long> out = new LinkedHashSet<>(a);
        out.removeAll(b);
        return out;
    }
}
