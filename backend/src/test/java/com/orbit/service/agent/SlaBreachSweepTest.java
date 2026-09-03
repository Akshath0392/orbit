package com.orbit.service.agent;

import com.orbit.domain.agent.AgentDefinition;
import com.orbit.domain.agent.CrEscalation;
import com.orbit.domain.config.StageSlaTarget;
import com.orbit.domain.darwin.LeaveRecord;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.AgentDefinitionRepository;
import com.orbit.repository.CrEscalationRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.LeaveRecordRepository;
import com.orbit.repository.StageSlaTargetRepository;
import com.orbit.service.agent.AgentRuntime;
import com.orbit.service.ai.AiGateway;
import com.orbit.service.am.SlaBucketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Detection core of the SLA-breach escalation loop. Pure —
 * no send, no agent invocation. Pins: breached found / near+met excluded,
 * cooldown dedup, owner-on-leave urgency, untracked-stage skip, near-margin opt-in.
 */
class SlaBreachSweepTest {

    private final JiraIssueRepository issues = mock(JiraIssueRepository.class);
    private final StageSlaTargetRepository targets = mock(StageSlaTargetRepository.class);
    private final CrEscalationRepository ledger = mock(CrEscalationRepository.class);
    private final LeaveRecordRepository leaves = mock(LeaveRecordRepository.class);
    private final SlaBucketService sla = new SlaBucketService();
    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final AgentDefinitionRepository agentDefs = mock(AgentDefinitionRepository.class);
    private final AiGateway ai = mock(AiGateway.class);

    private SlaBreachSweep sweep;
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 17, 12, 0);

    @BeforeEach
    void setUp() {
        sweep = new SlaBreachSweep(issues, targets, ledger, leaves, sla, runtime, agentDefs, ai);
        ReflectionTestUtils.setField(sweep, "enabled", true);
        ReflectionTestUtils.setField(sweep, "cooldownDays", 3);
        ReflectionTestUtils.setField(sweep, "nearMarginPct", 0);
        when(targets.findAll()).thenReturn(List.of(target("In Progress", 45)));
        when(ledger.findByLastProposedAtGreaterThanEqual(any())).thenReturn(List.of());
        when(leaves.findByStartDateBetweenOrderByStartDateAsc(any(), any())).thenReturn(List.of());
    }

    @Test
    void breachedFoundNearAndMetExcluded() {
        when(issues.findOpenCrsForEscalation()).thenReturn(List.of(
            cr("CR-1", "In Progress", 70, "Asha"),  // breached (age > 45)
            cr("CR-2", "In Progress", 40, "Ravi"),  // near (0.75*45 < 40 <= 45) — excluded at margin 0
            cr("CR-3", "In Progress", 5, "Ravi")     // met
        ));
        var c = sweep.findCandidates(now);
        assertThat(c).extracting(SlaBreachSweep.Candidate::issueKey).containsExactly("CR-1");
        assertThat(c.get(0).bucket()).isEqualTo(SlaBucketService.Bucket.BREACHED);
        assertThat(c.get(0).urgency()).isEqualTo("MEDIUM");
    }

    @Test
    void cooldownExcludesRecentlyProposed() {
        when(ledger.findByLastProposedAtGreaterThanEqual(any()))
            .thenReturn(List.of(new CrEscalation("CR-1", now.minusDays(1))));
        when(issues.findOpenCrsForEscalation()).thenReturn(List.of(cr("CR-1", "In Progress", 70, "Asha")));
        assertThat(sweep.findCandidates(now)).isEmpty();
    }

    @Test
    void ownerOnLeaveIsHighUrgency() {
        when(leaves.findByStartDateBetweenOrderByStartDateAsc(any(), any()))
            .thenReturn(List.of(leaveFor("Asha")));
        when(issues.findOpenCrsForEscalation()).thenReturn(List.of(cr("CR-1", "In Progress", 70, "Asha")));
        var c = sweep.findCandidates(now);
        assertThat(c.get(0).ownerOnLeave()).isTrue();
        assertThat(c.get(0).urgency()).isEqualTo("HIGH");
    }

    @Test
    void untrackedStageNeverEscalates() {
        // "Hold" has no SLA target → classify returns null → skipped, even at age 90.
        when(issues.findOpenCrsForEscalation()).thenReturn(List.of(cr("CR-1", "Hold", 90, "Asha")));
        assertThat(sweep.findCandidates(now)).isEmpty();
    }

    @Test
    void nearWithinMarginIncludedWhenConfigured() {
        ReflectionTestUtils.setField(sweep, "nearMarginPct", 10); // escalate NEAR within 10% of target
        when(issues.findOpenCrsForEscalation()).thenReturn(List.of(
            cr("CR-2", "In Progress", 42, "Ravi"),  // near, age 42 >= 45*0.9=40.5 → worthy
            cr("CR-4", "In Progress", 35, "Ravi")    // near, age 35 < 40.5 → still excluded
        ));
        var c = sweep.findCandidates(now);
        assertThat(c).extracting(SlaBreachSweep.Candidate::issueKey).containsExactly("CR-2");
        assertThat(c.get(0).bucket()).isEqualTo(SlaBucketService.Bucket.NEAR);
        assertThat(c.get(0).urgency()).isEqualTo("LOW");
    }

    // ── Wave 3: send path (HITL) ────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void sweepProposesViaRuntimeAndStampsLedgerWithoutSending() {
        AgentDefinition def = new AgentDefinition();
        def.setName(SlaBreachSweep.DEF_NAME);
        when(agentDefs.findByName(SlaBreachSweep.DEF_NAME)).thenReturn(Optional.of(def));
        when(ai.complete(any(), any())).thenReturn("Heads up: CR-1 is past SLA, please unblock.");
        when(issues.findOpenCrsForEscalation()).thenReturn(List.of(cr("CR-1", "In Progress", 70, "Asha")));

        sweep.sweep();

        // The runtime is asked to run the escalation def with the drafted message as input;
        // it (not this class) queues slack.send_channel AWAITING_HITL — nothing is sent here.
        ArgumentCaptor<Map<String, Object>> input = ArgumentCaptor.forClass(Map.class);
        verify(runtime, times(1)).execute(eq(def), any(), eq("CRON"), input.capture(), eq("SCHEDULED"));
        assertThat(input.getValue().get("message")).asString().contains("CR-1");
        assertThat(input.getValue().get("issueKey")).isEqualTo("CR-1");

        // dedup ledger stamped so the next sweep won't re-propose within cooldown
        ArgumentCaptor<CrEscalation> row = ArgumentCaptor.forClass(CrEscalation.class);
        verify(ledger, times(1)).save(row.capture());
        assertThat(row.getValue().getIssueKey()).isEqualTo("CR-1");
        assertThat(row.getValue().getLastProposedAt()).isNotNull();
    }

    @Test
    void disabledSweepDoesNothing() {
        ReflectionTestUtils.setField(sweep, "enabled", false);
        when(issues.findOpenCrsForEscalation()).thenReturn(List.of(cr("CR-1", "In Progress", 70, "Asha")));

        sweep.sweep();

        verify(runtime, never()).execute(any(), any(), any(), any(), any());
        verify(ledger, never()).save(any());
    }

    @Test
    void draftFallsBackToTemplateWhenAiFails() {
        AgentDefinition def = new AgentDefinition();
        when(agentDefs.findByName(SlaBreachSweep.DEF_NAME)).thenReturn(Optional.of(def));
        when(ai.complete(any(), any())).thenThrow(new RuntimeException("no AI provider"));
        when(issues.findOpenCrsForEscalation()).thenReturn(List.of(cr("CR-1", "In Progress", 70, "Asha")));

        // A failed AI draft must not block the escalation — runtime is still invoked.
        sweep.sweep();
        verify(runtime, times(1)).execute(any(), any(), eq("CRON"), any(), eq("SCHEDULED"));
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private JiraIssue cr(String key, String stage, int ageDays, String owner) {
        JiraIssue j = new JiraIssue();
        j.setIssueKey(key);
        j.setIssueType("CR");
        j.setLifecycleStage(stage);
        j.setCreatedAt(now.minusDays(ageDays));
        j.setAssigneeName(owner);
        return j;
    }

    private static StageSlaTarget target(String stage, int days) {
        StageSlaTarget t = new StageSlaTarget();
        t.setStage(stage);
        t.setTargetDays(days);
        return t;
    }

    private LeaveRecord leaveFor(String name) {
        LeaveRecord l = new LeaveRecord();
        l.setDarwinEmpId(name); // user null → sweep falls back to darwinEmpId
        l.setStartDate(now.toLocalDate());
        l.setEndDate(now.toLocalDate());
        return l;
    }
}
