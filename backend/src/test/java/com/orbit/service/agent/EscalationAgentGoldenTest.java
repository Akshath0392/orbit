package com.orbit.service.agent;

import com.orbit.domain.agent.AgentDecisionLog;
import com.orbit.domain.alert.Alert;
import com.orbit.repository.AgentDecisionLogRepository;
import com.orbit.repository.AlertRepository;
import com.orbit.service.ai.RecordedAiGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EscalationAgentGoldenTest {

    AlertRepository alerts;
    AgentDecisionLogRepository decisions;
    SimpMessagingTemplate ws;
    RecordedAiGateway ai;
    EscalationAgent agent;

    @BeforeEach
    void setUp() {
        alerts = mock(AlertRepository.class);
        decisions = mock(AgentDecisionLogRepository.class);
        ws = mock(SimpMessagingTemplate.class);
        ai = new RecordedAiGateway()
            .stub("system contains:Escalation Agent", "Hi team — CRITICAL alert: client X needs attention. Action: assign owner.");
        when(decisions.save(any(AgentDecisionLog.class))).thenAnswer(inv -> inv.getArgument(0));
        agent = new EscalationAgent(ai, ws, alerts, decisions);
        ReflectionTestUtils.setField(agent, "requireHitl", true);
    }

    @Test
    void emits_hitl_proposal_and_writes_decision_log_without_sending() {
        Alert alert = new Alert();
        ReflectionTestUtils.setField(alert, "id", 42L);
        alert.setSeverity("CRITICAL");
        alert.setTitle("SLA breach on ORD-101");
        alert.setDetail("4h overdue");
        when(alerts.findById(42L)).thenReturn(Optional.of(alert));

        agent.triggerEscalation(42L);

        // LLM was prompted exactly once with the escalation system prompt
        assertThat(ai.calls()).hasSize(1);
        assertThat(ai.lastCall().userMessage()).contains("SLA breach on ORD-101", "CRITICAL");

        // Decision log persisted with the drafted message embedded
        ArgumentCaptor<AgentDecisionLog> logCap = ArgumentCaptor.forClass(AgentDecisionLog.class);
        verify(decisions).save(logCap.capture());
        assertThat(logCap.getValue().getProposalJson()).contains("send_notification", "CRITICAL");
        assertThat(logCap.getValue().getOutcome()).isNull(); // never auto-approved

        // HITL proposal event broadcast; no Slack send call
        ArgumentCaptor<Object> evtCap = ArgumentCaptor.forClass(Object.class);
        verify(ws).convertAndSend(eq("/topic/copilot/default"), evtCap.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) evtCap.getValue();
        assertThat(event.get("type")).isEqualTo("proposal");
        assertThat(event.get("action")).isEqualTo("send_notification");
    }

    @Test
    void missing_alert_is_a_no_op() {
        when(alerts.findById(99L)).thenReturn(Optional.empty());
        agent.triggerEscalation(99L);
        assertThat(ai.calls()).isEmpty();
        verifyNoInteractions(ws);
        verify(decisions, never()).save(any());
    }

    @Test
    void refuses_to_run_when_hitl_safety_disabled() {
        ReflectionTestUtils.setField(agent, "requireHitl", false);
        try {
            agent.triggerEscalation(1L);
            org.assertj.core.api.Assertions.fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage()).contains("require-hitl");
        }
    }
}
