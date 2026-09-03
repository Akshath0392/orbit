package com.orbit.service.agent;

import com.orbit.service.agent.event.AgentRunStepEvent;
import com.orbit.service.agent.event.HitlAwaitingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgentRunStreamBridgeTest {

    SimpMessagingTemplate ws;
    AgentRunStreamBridge bridge;

    @BeforeEach
    void setUp() {
        ws = mock(SimpMessagingTemplate.class);
        bridge = new AgentRunStreamBridge(ws);
    }

    @Test
    void on_step_forwards_to_per_run_topic_with_event_payload() {
        AgentRunStepEvent ev = AgentRunStepEvent.completed(42L, 99L, "slack.send_channel", "ok");
        bridge.onStep(ev);

        ArgumentCaptor<AgentRunStepEvent> payload = ArgumentCaptor.forClass(AgentRunStepEvent.class);
        verify(ws).convertAndSend(eq("/topic/agent-runs/42"), payload.capture());
        assertThat(payload.getValue().status()).isEqualTo("COMPLETED");
        assertThat(payload.getValue().toolName()).isEqualTo("slack.send_channel");
    }

    @Test
    void on_step_skips_when_run_id_is_null() {
        bridge.onStep(new AgentRunStepEvent(null, 1L, "t", "STARTED", null, Instant.now()));
        verifyNoInteractions(ws);
    }

    @Test
    void on_hitl_republishes_as_awaiting_hitl_step_on_same_topic() {
        bridge.onHitlAwaiting(new HitlAwaitingEvent(7L, 8L, "EscAgent", "email.send", "{}", "alerts.engine"));

        ArgumentCaptor<AgentRunStepEvent> payload = ArgumentCaptor.forClass(AgentRunStepEvent.class);
        verify(ws).convertAndSend(eq("/topic/agent-runs/7"), payload.capture());
        assertThat(payload.getValue().status()).isEqualTo("AWAITING_HITL");
        assertThat(payload.getValue().toolName()).isEqualTo("email.send");
        assertThat(payload.getValue().message()).contains("alerts.engine");
    }

    @Test
    void runtime_exception_from_ws_is_swallowed_so_run_persistence_is_not_rolled_back() {
        doThrow(new RuntimeException("ws down")).when(ws).convertAndSend(any(String.class), any(Object.class));
        bridge.onStep(AgentRunStepEvent.failed(1L, 2L, "t", "boom"));
        bridge.onHitlAwaiting(new HitlAwaitingEvent(1L, 2L, "A", "t", "{}", "x"));
        // No exception escapes.
    }
}
