package com.orbit.service.sync;

import com.orbit.domain.client.LifecycleMapping;
import com.orbit.domain.issue.IssueTransition;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.IssueTransitionRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.LifecycleMappingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F3 step 3 — ledger semantics: dedup on changelog id, first-in-progress from
 * the earliest in-progress transition, reopen_count = closed→non-closed
 * transitions, both RECOMPUTED from the full ledger (replay-safe).
 */
class IssueTransitionServiceTest {

    private final IssueTransitionRepository transitions = mock(IssueTransitionRepository.class);
    private final JiraIssueRepository issues = mock(JiraIssueRepository.class);
    private final LifecycleMappingRepository lifecycle = mock(LifecycleMappingRepository.class);
    private final SprintIngestService sprintIngest = mock(SprintIngestService.class);

    private final IssueTransitionService service =
        new IssueTransitionService(transitions, issues, lifecycle, sprintIngest);

    private static JiraIssue issue(long id) {
        JiraIssue j = new JiraIssue();
        org.springframework.test.util.ReflectionTestUtils.setField(j, "id", id);
        return j;
    }

    private static IssueTransition status(String from, String to, LocalDateTime at) {
        IssueTransition t = new IssueTransition();
        t.setFieldType(IssueTransition.STATUS);
        t.setFromValue(from);
        t.setToValue(to);
        t.setTransitionedAt(at);
        return t;
    }

    private static LifecycleMapping mapping(String jiraStatus, String category) {
        LifecycleMapping m = new LifecycleMapping();
        m.setJiraStatus(jiraStatus);
        m.setCategory(category);
        return m;
    }

    @Test
    void recomputesFirstInProgressAndReopenCountFromLedger() {
        JiraIssue j = issue(7L);
        LocalDateTime t0 = LocalDateTime.of(2026, 6, 1, 9, 0);
        when(lifecycle.findAll()).thenReturn(List.of(
            mapping("In Progress", "in-progress"), mapping("Closed", "closed")));
        when(transitions.findByIssueIdAndFieldTypeOrderByTransitionedAtAsc(7L, IssueTransition.STATUS))
            .thenReturn(List.of(
                status("To Do", "In Progress", t0),                 // work starts
                status("In Progress", "Closed", t0.plusDays(3)),
                status("Closed", "In Progress", t0.plusDays(5)),    // reopen #1
                status("In Progress", "Closed", t0.plusDays(6)),
                status("Closed", "Reopened", t0.plusDays(8))        // reopen #2 (unmapped to-status)
            ));

        service.recomputeDerived(j);

        assertThat(j.getFirstInProgressAt()).isEqualTo(t0);
        assertThat(j.getReopenCount()).isEqualTo(2);
        verify(issues).save(j);
    }

    @Test
    void recordDedupsOnChangelogIdAndRoutesSprintDiffs() {
        JiraIssue j = issue(7L);
        when(transitions.existsByIssueIdAndChangelogIdAndFieldType(7L, "100", IssueTransition.STATUS))
            .thenReturn(true); // already ledgered — replay
        when(transitions.existsByIssueIdAndChangelogIdAndFieldType(7L, "101", IssueTransition.SPRINT))
            .thenReturn(false);
        when(lifecycle.findAll()).thenReturn(List.of());
        when(transitions.findByIssueIdAndFieldTypeOrderByTransitionedAtAsc(anyLong(), anyString()))
            .thenReturn(List.of());
        LocalDateTime at = LocalDateTime.of(2026, 7, 1, 10, 0);

        int written = service.record(j, List.of(
            new IssueTransitionService.ChangeItem("100", IssueTransition.STATUS, "To Do", "In Progress", at, "Asha"),
            new IssueTransitionService.ChangeItem("101", IssueTransition.SPRINT, "41", "41, 42", at, "Asha")
        ));

        assertThat(written).isEqualTo(1); // status row deduped, sprint row written
        verify(sprintIngest).applyMembershipDiff(j, "41", "41, 42", at);
        ArgumentCaptor<IssueTransition> saved = ArgumentCaptor.forClass(IssueTransition.class);
        verify(transitions).save(saved.capture());
        assertThat(saved.getValue().getFieldType()).isEqualTo(IssueTransition.SPRINT);
    }

    @Test
    void parsesWebhookChangelogHistories() {
        Map<String, Object> history = Map.of(
            "id", 4321,
            "created", "2026-07-10T09:15:00.000+0530",
            "author", Map.of("displayName", "Ravi"),
            "items", List.of(
                Map.of("field", "status", "fromString", "To Do", "toString", "In Progress"),
                Map.of("field", "Sprint", "from", "41", "to", "41, 42",
                       "fromString", "S27", "toString", "S27, S28"),
                Map.of("field", "Story point estimate", "fromString", "3", "toString", "5"),
                Map.of("field", "assignee", "fromString", "A", "toString", "B") // untracked
            ));

        List<IssueTransitionService.ChangeItem> items = service.parseHistory(history);

        assertThat(items).hasSize(3);
        assertThat(items.get(0).fieldType()).isEqualTo(IssueTransition.STATUS);
        assertThat(items.get(1).fieldType()).isEqualTo(IssueTransition.SPRINT);
        assertThat(items.get(1).from()).isEqualTo("41");   // sprint diffs use raw id lists
        assertThat(items.get(1).to()).isEqualTo("41, 42");
        assertThat(items.get(2).fieldType()).isEqualTo(IssueTransition.STORY_POINTS);
        assertThat(items.get(0).changelogId()).isEqualTo("4321");
        assertThat(items.get(0).by()).isEqualTo("Ravi");
    }
}
