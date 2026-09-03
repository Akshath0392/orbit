package com.orbit.service;

import com.orbit.domain.client.Project;
import com.orbit.domain.config.StageSlaTarget;
import com.orbit.repository.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W18 sprint scope — every CR lands in exactly one phase derived from its
 * lifecycle stage (widget-parity plan: backlog→Solutioning, dev/qa splits,
 * uat/ready→Production release, resolved→Delivered).
 */
class AccountDetailServiceSprintScopeTest {

    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final JiraIssueRepository issues = mock(JiraIssueRepository.class);
    private final StageSlaTargetRepository slaTargets = mock(StageSlaTargetRepository.class);

    private final AccountDetailService service = new AccountDetailService(
        projects, issues,
        mock(ManDayBudgetRepository.class), mock(ManDaySnapshotRepository.class),
        mock(ProjectTeamRepository.class), mock(ProjectRiskRepository.class),
        mock(ProjectReleaseRepository.class), mock(ProjectWinRepository.class),
        mock(GovernanceMeetingRepository.class), mock(ProjectHealthService.class),
        slaTargets,
        mock(com.orbit.service.dashboard.RiskScoringService.class),
        mock(AlertRepository.class), mock(UatCycleRepository.class));

    private static Object[] row(String key, String stage, int ageDays, LocalDateTime resolved) {
        return new Object[]{key, key + " summary", stage, stage,
            LocalDateTime.now().minusDays(ageDays), resolved, "Asha"};
    }

    private static StageSlaTarget target(String stage, int days) {
        StageSlaTarget t = new StageSlaTarget();
        t.setStage(stage);
        t.setTargetDays(days);
        return t;
    }

    @Test
    @SuppressWarnings("unchecked")
    void groupsEveryCrIntoExactlyOnePhase() {
        when(projects.findById(9L)).thenReturn(Optional.of(new Project()));
        when(slaTargets.findAll()).thenReturn(List.of(target("In dev", 45)));
        when(issues.findSprintScopeRowsForProject(eq(9L), any())).thenReturn(List.<Object[]>of(
            row("CR-1", "BRD awaited", 10, null),                  // Solutioning
            row("CR-2", "Finance approval Pending", 5, null),      // Solutioning
            row("CR-3", "In dev", 90, null),                       // Development, delayed (>45)
            row("CR-4", "Hold", 30, null),                         // Development, on-hold
            row("CR-5", "QA Review in progress - Staging", 8, null), // QA
            row("CR-6", "UAT in progress", 12, null),              // Production release
            row("CR-7", "Fixed", 3, null),                         // Production release
            row("CR-8", "Released", 40, LocalDateTime.now().minusDays(2)) // Delivered
        ));

        Map<String, Object> out = service.sprintScope(9L).orElseThrow();
        List<Map<String, Object>> phases = (List<Map<String, Object>>) out.get("phases");

        assertThat(out.get("total")).isEqualTo(8);
        assertThat(phases).extracting(p -> p.get("phase")).containsExactly(
            "Solutioning", "Development", "QA", "Production release", "Delivered");
        assertThat(phases.stream().mapToInt(p -> (int) p.get("count")).sum()).isEqualTo(8);
        assertThat((int) phases.get(0).get("count")).isEqualTo(2);
        assertThat((int) phases.get(1).get("count")).isEqualTo(2);
        assertThat((int) phases.get(2).get("count")).isEqualTo(1);
        assertThat((int) phases.get(3).get("count")).isEqualTo(2);
        assertThat((int) phases.get(4).get("count")).isEqualTo(1);

        List<Map<String, Object>> dev = (List<Map<String, Object>>) phases.get(1).get("rows");
        assertThat(dev).extracting(r -> r.get("badge")).containsExactlyInAnyOrder("delayed", "on-hold");
        List<Map<String, Object>> delivered = (List<Map<String, Object>>) phases.get(4).get("rows");
        assertThat(delivered.get(0).get("badge")).isEqualTo("delivered");
    }
}
