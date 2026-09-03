package com.orbit.service.agent;

import com.orbit.domain.agent.AgentDecisionLog;
import com.orbit.domain.capacity.ManDaySnapshot;
import com.orbit.domain.client.ManDayBudget;
import com.orbit.domain.client.Project;
import com.orbit.repository.AgentDecisionLogRepository;
import com.orbit.repository.ManDayBudgetRepository;
import com.orbit.repository.ManDaySnapshotRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.service.ai.RecordedAiGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ManDayForecastAgentGoldenTest {

    ProjectRepository projects;
    ManDaySnapshotRepository snapshots;
    ManDayBudgetRepository budgets;
    AgentDecisionLogRepository decisions;
    SimpMessagingTemplate ws;
    RecordedAiGateway ai;
    ManDayForecastAgent agent;

    @BeforeEach
    void setUp() {
        projects = mock(ProjectRepository.class);
        snapshots = mock(ManDaySnapshotRepository.class);
        budgets = mock(ManDayBudgetRepository.class);
        decisions = mock(AgentDecisionLogRepository.class);
        ws = mock(SimpMessagingTemplate.class);
        ai = new RecordedAiGateway().defaultResponse("Burn is accelerating. Intervention required within 14 days.");
        when(decisions.save(any(AgentDecisionLog.class))).thenAnswer(inv -> inv.getArgument(0));
        agent = new ManDayForecastAgent(ai, ws, projects, snapshots, budgets, decisions);
    }

    private static Project project(Long id, String name) {
        Project p = new Project();
        ReflectionTestUtils.setField(p, "id", id);
        p.setName(name);
        p.setActive(true);
        return p;
    }

    private static ManDaySnapshot snapshot(LocalDate date, double burned) {
        ManDaySnapshot s = new ManDaySnapshot();
        ReflectionTestUtils.setField(s, "snapshotDate", date);
        ReflectionTestUtils.setField(s, "burnedDays", BigDecimal.valueOf(burned));
        return s;
    }

    @Test
    void writes_decision_log_with_interpretation_and_does_not_alert_below_threshold() {
        Project p = project(5L, "Apollo");
        when(projects.findByActiveTrue()).thenReturn(List.of(p));
        // 7 days, accelerating burn — newest-first per repo contract
        when(snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(5L)).thenReturn(List.of(
            snapshot(LocalDate.of(2026, 6, 25), 32),
            snapshot(LocalDate.of(2026, 6, 24), 28),
            snapshot(LocalDate.of(2026, 6, 23), 24),
            snapshot(LocalDate.of(2026, 6, 22), 20),
            snapshot(LocalDate.of(2026, 6, 21), 16),
            snapshot(LocalDate.of(2026, 6, 20), 12),
            snapshot(LocalDate.of(2026, 6, 19), 8)
        ));
        ManDayBudget b = new ManDayBudget();
        b.setPurchasedDays(BigDecimal.valueOf(200));
        b.setAlertThresholdPct(80);
        when(budgets.findByProjectId(5L)).thenReturn(Optional.of(b));

        agent.runDailyForecast();

        // LLM interpretation called
        assertThat(ai.calls()).hasSize(1);
        // Decision log persisted with interpretation embedded
        ArgumentCaptor<AgentDecisionLog> logCap = ArgumentCaptor.forClass(AgentDecisionLog.class);
        verify(decisions).save(logCap.capture());
        assertThat(logCap.getValue().getAgentName()).isEqualTo("ManDayForecastAgent");
        assertThat(logCap.getValue().getProposalJson()).contains("Apollo", "Burn is accelerating");
        // Burn 32/200 = 16% — well below threshold, no alert
        verify(ws, never()).convertAndSend(eq("/topic/alerts/budget"), any(Object.class));
    }

    @Test
    void emits_budget_alert_when_burn_exceeds_threshold() {
        Project p = project(6L, "Borealis");
        when(projects.findByActiveTrue()).thenReturn(List.of(p));
        when(snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(6L)).thenReturn(List.of(
            snapshot(LocalDate.of(2026, 6, 25), 170),
            snapshot(LocalDate.of(2026, 6, 24), 165),
            snapshot(LocalDate.of(2026, 6, 23), 160),
            snapshot(LocalDate.of(2026, 6, 22), 155)
        ));
        ManDayBudget b = new ManDayBudget();
        b.setPurchasedDays(BigDecimal.valueOf(200));
        b.setAlertThresholdPct(80);
        when(budgets.findByProjectId(6L)).thenReturn(Optional.of(b));

        agent.runDailyForecast();

        ArgumentCaptor<Object> eventCap = ArgumentCaptor.forClass(Object.class);
        verify(ws).convertAndSend(eq("/topic/alerts/budget"), eventCap.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) eventCap.getValue();
        assertThat(event.get("type")).isEqualTo("budget_alert");
        assertThat(event.get("projectName")).isEqualTo("Borealis");
    }

    @Test
    void skips_project_with_too_few_snapshots() {
        Project p = project(7L, "Comet");
        when(projects.findByActiveTrue()).thenReturn(List.of(p));
        when(snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(7L)).thenReturn(List.of(
            snapshot(LocalDate.of(2026, 6, 25), 10),
            snapshot(LocalDate.of(2026, 6, 24), 8)
        ));
        agent.runDailyForecast();
        assertThat(ai.calls()).isEmpty();
        verify(decisions, never()).save(any());
    }
}
