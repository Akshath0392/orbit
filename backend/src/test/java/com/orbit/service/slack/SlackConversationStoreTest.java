package com.orbit.service.slack;

import com.orbit.domain.agent.AgentMemory;
import com.orbit.repository.AgentMemoryRepository;
import com.orbit.service.slack.SlackConversationStore.SlackTurnContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SlackConversationStoreTest {

    AgentMemoryRepository repo;
    SlackConversationStore store;

    @BeforeEach
    void setUp() {
        repo = mock(AgentMemoryRepository.class);
        store = new SlackConversationStore(repo);
    }

    @Test
    void thread_key_prefers_thread_ts_then_channel() {
        assertThat(store.threadKey("C9", "1700.001")).isEqualTo("slack_thread:1700.001");
        assertThat(store.threadKey("D9", null)).isEqualTo("slack_thread:D9");
    }

    @Test
    void save_writes_agent_memory_row_with_slack_thread_type_and_ttl() {
        store.save("slack_thread:t1",
            new SlackTurnContext("orbit.get_alerts", "Apollo", "critical", null));

        ArgumentCaptor<AgentMemory> cap = ArgumentCaptor.forClass(AgentMemory.class);
        verify(repo).save(cap.capture());
        AgentMemory saved = cap.getValue();
        assertThat(saved.getMemoryType()).isEqualTo("SLACK_THREAD");
        assertThat(saved.getMemKey()).isEqualTo("slack_thread:t1");
        assertThat(saved.getMemValue()).contains("orbit.get_alerts", "Apollo", "critical");
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void load_returns_latest_non_expired_row() {
        AgentMemory expired = new AgentMemory();
        expired.setMemoryType("SLACK_THREAD");
        expired.setMemKey("slack_thread:t1");
        expired.setMemValue("{\"lastTool\":\"old\"}");
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(5));

        AgentMemory fresh = new AgentMemory();
        fresh.setMemoryType("SLACK_THREAD");
        fresh.setMemKey("slack_thread:t1");
        fresh.setMemValue("{\"lastTool\":\"orbit.get_bugs\",\"projectName\":\"Apollo\",\"severity\":\"P0\"}");
        fresh.setExpiresAt(LocalDateTime.now().plusMinutes(30));

        when(repo.findByMemoryTypeAndMemKey("SLACK_THREAD", "slack_thread:t1"))
            .thenReturn(List.of(fresh, expired));

        Optional<SlackTurnContext> ctx = store.load("slack_thread:t1");
        assertThat(ctx).isPresent();
        assertThat(ctx.get().lastTool()).isEqualTo("orbit.get_bugs");
        assertThat(ctx.get().projectName()).isEqualTo("Apollo");
        assertThat(ctx.get().severity()).isEqualTo("P0");
    }

    @Test
    void load_skips_only_expired_rows_and_returns_empty_when_all_expired() {
        AgentMemory expired = new AgentMemory();
        expired.setMemoryType("SLACK_THREAD");
        expired.setMemKey("slack_thread:t1");
        expired.setMemValue("{\"lastTool\":\"x\"}");
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(repo.findByMemoryTypeAndMemKey(any(), any())).thenReturn(List.of(expired));
        assertThat(store.load("slack_thread:t1")).isEmpty();
    }

    @Test
    void inheritable_args_only_emits_non_blank_fields() {
        var ctx = new SlackTurnContext("orbit.get_bugs", "Apollo", null, null);
        assertThat(ctx.inheritableArgs()).containsEntry("projectName", "Apollo").doesNotContainKey("severity");

        var empty = new SlackTurnContext("orbit.get_bugs", null, null, null);
        assertThat(empty.inheritableArgs()).isEmpty();
    }
}
