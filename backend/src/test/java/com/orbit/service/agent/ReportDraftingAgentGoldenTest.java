package com.orbit.service.agent;

import com.orbit.domain.agent.AgentDecisionLog;
import com.orbit.domain.report.GeneratedReport;
import com.orbit.repository.AgentDecisionLogRepository;
import com.orbit.repository.AlertRepository;
import com.orbit.repository.GeneratedReportRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.ManDayBudgetRepository;
import com.orbit.service.ai.RecordedAiGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReportDraftingAgentGoldenTest {

    GeneratedReportRepository reports;
    AlertRepository alerts;
    JiraIssueRepository jira;
    ManDayBudgetRepository budgets;
    AgentDecisionLogRepository decisions;
    SimpMessagingTemplate ws;
    RecordedAiGateway ai;
    ReportDraftingAgent agent;

    private static final String FOUR_SECTION_RESPONSE = """
            ## Executive Summary
            Project is tracking on plan; one CRITICAL alert active.

            ## CR Status
            3 CRs in flight; NX-101 awaiting client sign-off.

            ## Bug Summary
            5 open prod bugs; severity distribution P0:1 P2:4.

            ## Capacity Risk
            Team at 92% utilisation — vacation gap in week 2 needs cover.
            """;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        reports = mock(GeneratedReportRepository.class);
        alerts = mock(AlertRepository.class);
        jira = mock(JiraIssueRepository.class);
        budgets = mock(ManDayBudgetRepository.class);
        decisions = mock(AgentDecisionLogRepository.class);
        ws = mock(SimpMessagingTemplate.class);
        ai = new RecordedAiGateway().defaultResponse(FOUR_SECTION_RESPONSE);
        when(decisions.save(any(AgentDecisionLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reports.save(any(GeneratedReport.class))).thenAnswer(inv -> inv.getArgument(0));
        Page<?> emptyPage = new PageImpl<>(List.of());
        when(alerts.findFiltered(any(), any(), any(), any(Pageable.class))).thenReturn((Page) emptyPage);
        when(jira.findCrs(any(), any(), any(), any(Pageable.class))).thenReturn((Page) emptyPage);
        when(budgets.findByProjectId(anyLong())).thenReturn(Optional.empty());
        agent = new ReportDraftingAgent(ai, ws, reports, alerts, jira, budgets, decisions);
    }

    @Test
    void produces_four_sections_and_emits_report_ready_event() {
        GeneratedReport r = GeneratedReport.builder().type("Weekly delivery").build();
        ReflectionTestUtils.setField(r, "id", 99L);
        r.setStatus("GENERATING");
        when(reports.findById(99L)).thenReturn(Optional.of(r));

        agent.draftReport(99L, "pjm@orbit.io");

        assertThat(ai.calls()).hasSize(1);
        assertThat(r.getStatus()).isEqualTo("DONE");
        // contentJson contains the four required section titles
        ArgumentCaptor<GeneratedReport> savedCap = ArgumentCaptor.forClass(GeneratedReport.class);
        verify(reports, atLeastOnce()).save(savedCap.capture());
        String content = savedCap.getAllValues().stream()
            .filter(g -> g.getContentJson() != null)
            .findFirst().orElseThrow().getContentJson();
        assertThat(content)
            .contains("Executive Summary", "CR Status", "Bug Summary", "Capacity Risk");

        // Decision log written
        verify(decisions).save(any(AgentDecisionLog.class));
        // report_ready emitted to user-scoped topic
        ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
        verify(ws).convertAndSend(eq("/topic/reports/pjm@orbit.io"), evt.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) evt.getValue();
        assertThat(event.get("type")).isEqualTo("report_ready");
        assertThat(event.get("reportId")).isEqualTo(99L);
    }

    @Test
    void missing_report_is_a_no_op() {
        when(reports.findById(123L)).thenReturn(Optional.empty());
        agent.draftReport(123L, "pjm@orbit.io");
        assertThat(ai.calls()).isEmpty();
        verifyNoInteractions(ws);
        verify(decisions, never()).save(any());
    }

    @Test
    void falls_back_to_four_stub_sections_when_llm_does_not_follow_format() {
        ai.defaultResponse("Hi, here is some unstructured prose without headings.");
        GeneratedReport r = GeneratedReport.builder().type("Adhoc").build();
        ReflectionTestUtils.setField(r, "id", 1L);
        when(reports.findById(1L)).thenReturn(Optional.of(r));

        agent.draftReport(1L, "pjm@orbit.io");

        ArgumentCaptor<GeneratedReport> cap = ArgumentCaptor.forClass(GeneratedReport.class);
        verify(reports, atLeastOnce()).save(cap.capture());
        String content = cap.getAllValues().stream()
            .filter(g -> g.getContentJson() != null)
            .findFirst().orElseThrow().getContentJson();
        assertThat(content)
            .contains("Executive Summary", "CR Status", "Bug Summary", "Capacity Risk");
    }
}
