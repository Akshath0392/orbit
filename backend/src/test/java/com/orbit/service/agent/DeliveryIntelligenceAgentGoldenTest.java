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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DeliveryIntelligenceAgentGoldenTest {

    AlertRepository alerts;
    AgentDecisionLogRepository decisions;
    SimpMessagingTemplate ws;
    RecordedAiGateway ai;
    DeliveryIntelligenceAgent agent;

    @BeforeEach
    void setUp() {
        alerts = mock(AlertRepository.class);
        decisions = mock(AgentDecisionLogRepository.class);
        ws = mock(SimpMessagingTemplate.class);
        ai = new RecordedAiGateway().defaultResponse("Bullet one. Bullet two.");
        when(decisions.save(any(AgentDecisionLog.class))).thenAnswer(inv -> inv.getArgument(0));
        agent = new DeliveryIntelligenceAgent(ai, ws, alerts, decisions);
    }

    private static Alert alert(Long id, String severity, String title) {
        Alert a = new Alert();
        ReflectionTestUtils.setField(a, "id", id);
        a.setSeverity(severity);
        a.setTitle(title);
        return a;
    }

    private void runSync(String trigger) throws Exception {
        Method m = DeliveryIntelligenceAgent.class.getDeclaredMethod("executeRun", String.class);
        m.setAccessible(true);
        m.invoke(agent, trigger);
    }

    @Test
    void streams_tokens_and_emits_escalation_for_each_critical_alert() throws Exception {
        when(alerts.findTop5ByStatusOrderByCreatedAtDesc("OPEN")).thenReturn(List.of(
            alert(10L, "CRITICAL", "SLA breach NX-101"),
            alert(11L, "WARNING",  "CR aging"),
            alert(12L, "CRITICAL", "Capacity overload")
        ));

        runSync("test");

        assertThat(ai.calls()).hasSize(1);
        ArgumentCaptor<Object> evtCap = ArgumentCaptor.forClass(Object.class);
        verify(ws, atLeast(2)).convertAndSend(eq("/topic/copilot/daily-briefing"), evtCap.capture());

        long tokens = evtCap.getAllValues().stream()
            .map(o -> (Map<?, ?>) o)
            .filter(m -> "token".equals(m.get("type")))
            .count();
        long proposals = evtCap.getAllValues().stream()
            .map(o -> (Map<?, ?>) o)
            .filter(m -> "proposal".equals(m.get("type")))
            .count();
        long dones = evtCap.getAllValues().stream()
            .map(o -> (Map<?, ?>) o)
            .filter(m -> "done".equals(m.get("type")))
            .count();

        assertThat(tokens).isGreaterThan(0);
        assertThat(proposals).isEqualTo(2);
        assertThat(dones).isEqualTo(1);
        verify(decisions, times(2)).save(any(AgentDecisionLog.class));
    }

    @Test
    void no_alerts_still_streams_and_terminates_with_done() throws Exception {
        when(alerts.findTop5ByStatusOrderByCreatedAtDesc("OPEN")).thenReturn(List.of());
        runSync("test");
        verify(decisions, never()).save(any());
        verify(ws, atLeastOnce()).convertAndSend(eq("/topic/copilot/daily-briefing"),
            argThat((Object e) -> e instanceof Map<?, ?> m && "done".equals(m.get("type"))));
    }
}
