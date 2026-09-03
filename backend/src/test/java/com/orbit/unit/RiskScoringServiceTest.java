package com.orbit.unit;

import com.orbit.domain.client.Client;
import com.orbit.domain.client.ManDayBudget;
import com.orbit.domain.client.Project;
import com.orbit.domain.capacity.Developer;
import com.orbit.domain.capacity.ManDaySnapshot;
import com.orbit.repository.*;
import com.orbit.service.dashboard.RiskScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the risk scoring algorithm.
 * Mocks all repositories — no DB required.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskScoringServiceTest {

    @Mock AlertRepository alerts;
    @Mock IssueMilestoneRepository milestones;
    @Mock JiraIssueRepository issues;
    @Mock ManDayBudgetRepository budgets;
    @Mock ManDaySnapshotRepository snapshots;
    @Mock DeveloperRepository developers;

    @InjectMocks RiskScoringService service;

    private Project project;

    @BeforeEach
    void setUp() {
        Client client = new Client();
        client.setName("Test Corp");
        // Reflectively set id=1
        org.springframework.test.util.ReflectionTestUtils.setField(client, "id", 1L);

        project = new Project();
        project.setName("Test Project");
        project.setClient(client);
        org.springframework.test.util.ReflectionTestUtils.setField(project, "id", 10L);

        // Default: no developers overloading
        Developer dev = new Developer();
        org.springframework.test.util.ReflectionTestUtils.setField(dev, "utilization", 50);
        when(developers.findAllByOrderByUtilizationDesc()).thenReturn(List.of(dev));
    }

    // ── Healthy project ───────────────────────────────────────────────────────

    @Test
    void healthyProjectScoresCorrectly() {
        stubNoAlerts();
        stubNoMilestoneIssues();
        stubBudget(100.0, 40.0);

        var result = service.score(project);

        assertThat(result.riskLevel()).isEqualTo("healthy");
        assertThat(result.slipProbabilityPct()).isLessThan(30);
        assertThat(result.burnPct()).isEqualTo(40);
    }

    // ── Critical project ──────────────────────────────────────────────────────

    @Test
    void criticalProjectWithManyAlertsScoresCorrectly() {
        stubProjectAlerts(3L, 3L, 2L);
        stubNoMilestoneIssues();
        stubBudget(100.0, 90.0);

        var result = service.score(project);

        assertThat(result.riskLevel()).isEqualTo("critical");
        assertThat(result.slipProbabilityPct()).isGreaterThan(70);
    }

    // ── Watch project ─────────────────────────────────────────────────────────

    @Test
    void watchProjectWithSomeAlertsScoresCorrectly() {
        stubProjectAlerts(1L, 1L, 1L);
        stubNoMilestoneIssues();
        stubBudget(100.0, 65.0);

        var result = service.score(project);

        assertThat(result.riskLevel()).isIn("watch", "critical");
        assertThat(result.slipProbabilityPct()).isBetween(30, 90);
    }

    // ── Burn % computation ────────────────────────────────────────────────────

    @Test
    void burnPctReflectsBudgetConsumption() {
        stubNoAlerts();
        stubNoMilestoneIssues();
        stubBudget(200.0, 150.0);

        var result = service.score(project);

        assertThat(result.burnPct()).isEqualTo(75);
    }

    @Test
    void noBudgetDefaultsTo50Percent() {
        stubNoAlerts();
        stubNoMilestoneIssues();
        when(budgets.findByProjectIdIn(anyList())).thenReturn(Collections.emptyList());
        when(snapshots.findTop14PerProject(anyList())).thenReturn(Collections.emptyList());

        var result = service.score(project);

        assertThat(result.burnPct()).isEqualTo(50);
    }

    // ── Heat strip ────────────────────────────────────────────────────────────

    @Test
    void heatStripAlwaysHas14Entries() {
        stubNoAlerts();
        stubNoMilestoneIssues();
        stubBudget(100.0, 50.0);

        var result = service.score(project);

        assertThat(result.heat()).hasSize(14);
    }

    // ── Score clamped 0–100 ───────────────────────────────────────────────────

    @Test
    void rawScoreIsAlwaysBetween0And100() {
        stubProjectAlerts(10L, 10L, 10L);
        stubNoMilestoneIssues();
        stubBudget(100.0, 99.0);

        var result = service.score(project);

        assertThat(result.rawScore()).isBetween(0, 100);
        assertThat(result.slipProbabilityPct()).isBetween(0, 100);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Grouped-query rows: project 10, critical OPEN / critical ACK / risk OPEN. */
    private void stubProjectAlerts(long criticalOpen, long criticalAck, long riskOpen) {
        when(alerts.countBySeverityAndStatusGroupedByProject(anyList())).thenReturn(List.of(
            new Object[]{10L, "critical", "OPEN",         criticalOpen},
            new Object[]{10L, "critical", "ACKNOWLEDGED", criticalAck},
            new Object[]{10L, "risk",     "OPEN",         riskOpen}));
        when(alerts.countBySeverityAndStatusGroupedByClient(anyList())).thenReturn(Collections.emptyList());
    }

    private void stubNoAlerts() {
        when(alerts.countBySeverityAndStatusGroupedByProject(anyList())).thenReturn(Collections.emptyList());
        when(alerts.countBySeverityAndStatusGroupedByClient(anyList())).thenReturn(Collections.emptyList());
    }

    private void stubNoMilestoneIssues() {
        when(milestones.countTbcGroupedByProject(anyList())).thenReturn(Collections.emptyList());
        when(milestones.countOverdueGroupedByProject(anyList())).thenReturn(Collections.emptyList());
        when(issues.countByProjectsTypeAndSlaStatusGrouped(anyList(), anyString(), anyString())).thenReturn(Collections.emptyList());
        when(issues.findHoldingCrsByProjects(anyList())).thenReturn(Collections.emptyList());
        when(issues.countByClientsTypeAndLifecycleStageNotGrouped(anyList(), anyString(), anyString())).thenReturn(Collections.emptyList());
    }

    private void stubBudget(double purchased, double burned) {
        ManDayBudget budget = ManDayBudget.builder()
            .purchasedDays(BigDecimal.valueOf(purchased))
            .alertThresholdPct(80)
            .build();
        org.springframework.test.util.ReflectionTestUtils.setField(budget, "project", project);
        ManDaySnapshot snap = new ManDaySnapshot();
        org.springframework.test.util.ReflectionTestUtils.setField(snap, "project", project);
        org.springframework.test.util.ReflectionTestUtils.setField(snap, "burnedDays", BigDecimal.valueOf(burned));
        org.springframework.test.util.ReflectionTestUtils.setField(snap, "remainingDays", BigDecimal.valueOf(purchased - burned));
        org.springframework.test.util.ReflectionTestUtils.setField(snap, "burnRatePerDay", BigDecimal.valueOf(1.2));
        org.springframework.test.util.ReflectionTestUtils.setField(snap, "forecastExhaustion", LocalDate.now().plusDays(20));

        when(budgets.findByProjectIdIn(anyList())).thenReturn(List.of(budget));
        when(snapshots.findTop14PerProject(anyList())).thenReturn(List.of(snap));
    }
}
