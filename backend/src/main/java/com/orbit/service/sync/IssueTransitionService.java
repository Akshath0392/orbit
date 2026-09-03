package com.orbit.service.sync;

import com.orbit.domain.issue.IssueTransition;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.IssueTransitionRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.LifecycleMappingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Writes the issue_transitions ledger (status / sprint / story-point changes)
 * and recomputes the derived issue values from it. Shared by the webhook and
 * the changelog backfill; UNIQUE(issue_id, changelog_id, field_type) plus the
 * exists-check makes both paths mutually idempotent, and derived values are
 * always recomputed from the full ledger — order-independent, replay-safe.
 */
@Service
public class IssueTransitionService {

    /** One parsed Jira changelog item for a tracked field. */
    public record ChangeItem(String changelogId, String fieldType, String from, String to,
                             LocalDateTime at, String by) {}

    private static final Set<String> CLOSED_CATEGORIES = Set.of("closed", "released");
    private static final Set<String> CLOSED_STATUS_NAMES =
        Set.of("released", "closed", "invalid", "resolved", "canceled", "rejected", "done",
               "released to production");
    private static final Set<String> IN_PROGRESS_HINTS =
        Set.of("in progress", "in dev", "development", "in qa");

    private final IssueTransitionRepository transitions;
    private final JiraIssueRepository issues;
    private final LifecycleMappingRepository lifecycle;
    private final SprintIngestService sprintIngest;

    public IssueTransitionService(IssueTransitionRepository transitions,
                                  JiraIssueRepository issues,
                                  LifecycleMappingRepository lifecycle,
                                  SprintIngestService sprintIngest) {
        this.transitions = transitions;
        this.issues = issues;
        this.lifecycle = lifecycle;
        this.sprintIngest = sprintIngest;
    }

    /** Records new ledger rows (deduped) and refreshes the issue's derived values. */
    @Transactional
    public int record(JiraIssue issue, List<ChangeItem> items) {
        int written = 0;
        for (ChangeItem item : items) {
            if (item.changelogId() != null && transitions.existsByIssueIdAndChangelogIdAndFieldType(
                    issue.getId(), item.changelogId(), item.fieldType())) {
                continue;
            }
            IssueTransition t = new IssueTransition();
            t.setIssueId(issue.getId());
            t.setFieldType(item.fieldType());
            t.setFromValue(item.from());
            t.setToValue(item.to());
            t.setChangelogId(item.changelogId());
            t.setTransitionedAt(item.at());
            t.setTransitionedBy(item.by());
            if (IssueTransition.STATUS.equals(item.fieldType())) {
                t.setFromStatus(truncate(item.from(), 50));
                t.setToStatus(truncate(item.to(), 50));
            }
            transitions.save(t);
            written++;
            if (IssueTransition.SPRINT.equals(item.fieldType())) {
                sprintIngest.applyMembershipDiff(issue, item.from(), item.to(), item.at());
            }
        }
        if (written > 0) recomputeDerived(issue);
        return written;
    }

    /**
     * Derived values from the full status ledger: first_in_progress_at = first
     * transition into an in-progress-ish status; reopen_count = closed →
     * non-closed transitions (the reopen smell). Recomputed, never incremented.
     */
    @Transactional
    public void recomputeDerived(JiraIssue issue) {
        Map<String, String> categoryByStatus = new HashMap<>();
        lifecycle.findAll().forEach(m -> {
            if (m.getJiraStatus() != null && m.getCategory() != null) {
                categoryByStatus.putIfAbsent(m.getJiraStatus().toLowerCase(), m.getCategory());
            }
        });

        LocalDateTime firstInProgress = null;
        int reopens = 0;
        for (IssueTransition t : transitions.findByIssueIdAndFieldTypeOrderByTransitionedAtAsc(
                issue.getId(), IssueTransition.STATUS)) {
            String to = t.getToValue() != null ? t.getToValue() : t.getToStatus();
            String from = t.getFromValue() != null ? t.getFromValue() : t.getFromStatus();
            if (firstInProgress == null && isInProgress(to, categoryByStatus)) {
                firstInProgress = t.getTransitionedAt();
            }
            if (isClosed(from, categoryByStatus) && to != null && !isClosed(to, categoryByStatus)) {
                reopens++;
            }
        }
        issue.setFirstInProgressAt(firstInProgress);
        issue.setReopenCount(reopens);
        issues.save(issue);
    }

    private boolean isClosed(String status, Map<String, String> categoryByStatus) {
        if (status == null || status.isBlank()) return false;
        String s = status.trim().toLowerCase();
        String category = categoryByStatus.get(s);
        if (category != null) return CLOSED_CATEGORIES.contains(category);
        return CLOSED_STATUS_NAMES.contains(s);
    }

    private boolean isInProgress(String status, Map<String, String> categoryByStatus) {
        if (status == null || status.isBlank()) return false;
        String s = status.trim().toLowerCase();
        String category = categoryByStatus.get(s);
        if (category != null) return "in-progress".equals(category);
        // V72 convention: unmapped statuses fall back to the in-progress bucket,
        // but for WORK-START detection require a dev-ish name, not just "not closed".
        return IN_PROGRESS_HINTS.stream().anyMatch(s::contains);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    // ── Jira changelog parsing (shared webhook/backfill shape) ──────────────

    private static final Set<String> STATUS_FIELDS = Set.of("status");
    private static final Set<String> SPRINT_FIELDS = Set.of("sprint");
    private static final Set<String> SP_FIELDS = Set.of("story points", "story point estimate");

    /**
     * Parses one Jira changelog history entry ({id, created, author, items[]})
     * into tracked ChangeItems. Works for both the webhook `changelog` block
     * and the REST /changelog endpoint's `values[]` entries.
     */
    @SuppressWarnings("unchecked")
    public List<ChangeItem> parseHistory(Map<String, Object> history) {
        List<ChangeItem> out = new ArrayList<>();
        if (history == null) return out;
        String id = history.get("id") == null ? null : String.valueOf(history.get("id"));
        LocalDateTime at = com.orbit.integration.jira.JiraDates.parse(history.get("created"));
        String by = null;
        if (history.get("author") instanceof Map<?, ?> author) {
            Object name = ((Map<String, Object>) author).get("displayName");
            if (name instanceof String s) by = s;
        }
        Object itemsRaw = history.get("items");
        if (!(itemsRaw instanceof List<?> items)) return out;
        for (Object o : items) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, Object> item = (Map<String, Object>) m;
            String field = item.get("field") == null ? "" : ((String) item.get("field")).toLowerCase();
            String from = str(item.get("fromString"));
            String to = str(item.get("toString"));
            if (STATUS_FIELDS.contains(field)) {
                out.add(new ChangeItem(id, IssueTransition.STATUS, from, to, at, by));
            } else if (SPRINT_FIELDS.contains(field)) {
                // Sprint items carry comma-separated sprint-ID lists in from/to
                out.add(new ChangeItem(id, IssueTransition.SPRINT, str(item.get("from")), str(item.get("to")), at, by));
            } else if (SP_FIELDS.contains(field)) {
                out.add(new ChangeItem(id, IssueTransition.STORY_POINTS, from, to, at, by));
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
