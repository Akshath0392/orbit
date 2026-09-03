package com.orbit.integration.slack;

import com.orbit.domain.config.SlackConfig;
import com.orbit.repository.SlackConfigRepository;
import com.orbit.service.agent.event.HitlAwaitingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SlackHitlBridgeTest {

    SlackConfigRepository configs;
    SlackClient slack;
    SlackResponseRenderer renderer = new SlackResponseRenderer();
    SlackHitlBridge bridge;

    @BeforeEach
    void setUp() {
        configs = mock(SlackConfigRepository.class);
        slack = mock(SlackClient.class);
        bridge = new SlackHitlBridge(configs, slack, renderer, "https://orbit.example.com");
    }

    private static SlackConfig cfg(String channel) {
        SlackConfig c = new SlackConfig();
        c.setEnabled(true);
        c.setDefaultChannel(channel);
        return c;
    }

    @Test
    void posts_approval_card_to_default_channel_when_slack_configured() {
        when(configs.findFirstByEnabledTrue()).thenReturn(Optional.of(cfg("#orbit-hitl")));
        when(slack.postMessage(any(), any(), any())).thenReturn(Map.of("ok", true, "ts", "1700.0", "channel", "C1"));

        bridge.onHitlAwaiting(new HitlAwaitingEvent(10L, 20L, "EscAgent", "email.send",
            "{\"to\":\"vp@orbit.io\"}", "alerts.engine"));

        verify(slack).postMessage(eq("#orbit-hitl"), any(), any());
    }

    @Test
    void skips_silently_when_no_default_channel_configured() {
        when(configs.findFirstByEnabledTrue()).thenReturn(Optional.of(cfg(null)));

        bridge.onHitlAwaiting(new HitlAwaitingEvent(10L, 20L, "EscAgent", "email.send", "{}", "x"));

        verifyNoInteractions(slack);
    }

    @Test
    void skips_silently_when_slack_disabled() {
        when(configs.findFirstByEnabledTrue()).thenReturn(Optional.empty());

        bridge.onHitlAwaiting(new HitlAwaitingEvent(10L, 20L, "EscAgent", "email.send", "{}", "x"));

        verifyNoInteractions(slack);
    }

    @Test
    void swallows_runtime_exception_from_slack_so_run_persistence_is_not_rolled_back() {
        when(configs.findFirstByEnabledTrue()).thenReturn(Optional.of(cfg("#x")));
        when(slack.postMessage(any(), any(), any())).thenThrow(new RuntimeException("network down"));

        bridge.onHitlAwaiting(new HitlAwaitingEvent(10L, 20L, "EscAgent", "email.send", "{}", "x"));
        // No exception escapes — listener is fire-and-forget.
    }
}
