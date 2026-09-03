package com.orbit.integration.slack;

import com.orbit.domain.config.SlackConfig;
import com.orbit.repository.SlackConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SlackClientTest {

    SlackConfigRepository configs;
    SlackEncryptionService enc;
    RecordingSlackClient client;

    static class RecordingSlackClient extends SlackClient {
        record Call(String endpoint, Map<String, Object> body) {}
        final List<Call> calls = new ArrayList<>();
        Map<String, Object> nextResponse = Map.of("ok", true, "ts", "1.000", "channel", "C9");
        RecordingSlackClient(SlackConfigRepository c, SlackEncryptionService e) { super(c, e); }
        @Override
        Map<String, Object> post(String endpoint, Map<String, Object> body) {
            calls.add(new Call(endpoint, body));
            return nextResponse;
        }
    }

    @BeforeEach
    void setUp() {
        configs = mock(SlackConfigRepository.class);
        enc = mock(SlackEncryptionService.class);
        SlackConfig cfg = new SlackConfig();
        cfg.setBotToken("enc:abc");
        when(configs.findFirstByEnabledTrue()).thenReturn(Optional.of(cfg));
        client = new RecordingSlackClient(configs, enc);
    }

    @Test
    void postMessage_sends_channel_text_and_blocks() {
        List<Map<String, Object>> blocks = List.of(Map.of("type", "section"));
        Map<String, Object> res = client.postMessage("C9", "fallback", blocks);
        assertThat(res).containsEntry("ok", true);
        assertThat(client.calls).hasSize(1);
        assertThat(client.calls.get(0).endpoint()).isEqualTo("chat.postMessage");
        assertThat(client.calls.get(0).body())
            .containsEntry("channel", "C9")
            .containsEntry("text", "fallback")
            .containsEntry("blocks", blocks);
    }

    @Test
    void postEphemeral_includes_user_field() {
        client.postEphemeral("C9", "U7", "fallback", List.of());
        assertThat(client.calls.get(0).endpoint()).isEqualTo("chat.postEphemeral");
        assertThat(client.calls.get(0).body()).containsEntry("user", "U7");
    }

    @Test
    void updateMessage_includes_ts_and_targets_chat_update() {
        client.updateMessage("C9", "1700000000.001", "fallback", List.of());
        assertThat(client.calls.get(0).endpoint()).isEqualTo("chat.update");
        assertThat(client.calls.get(0).body()).containsEntry("ts", "1700000000.001");
    }

    @Test
    void postInThread_targets_chat_postMessage_with_thread_ts() {
        client.postInThread("C9", "1700.001", "fallback", List.of());
        assertThat(client.calls.get(0).endpoint()).isEqualTo("chat.postMessage");
        assertThat(client.calls.get(0).body()).containsEntry("thread_ts", "1700.001");
    }
}
