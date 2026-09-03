package com.orbit.service.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.agent.AgentMemory;
import com.orbit.repository.AgentMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Per-thread short-term memory for Slack conversations. Lets follow-up turns
 * inherit context from the previous turn ("now show bugs", "and for Mobile SDK").
 *
 * Persisted via {@link AgentMemory} with {@code memory_type='SLACK_THREAD'} and
 * {@code mem_key='slack_thread:<thread_ts>'} (DMs use the channel id when no thread).
 * Entries expire after 1 hour — Slack threads rarely outlive that for live tasks.
 */
@Service
public class SlackConversationStore {

    private static final Logger log = LoggerFactory.getLogger(SlackConversationStore.class);
    private static final String MEMORY_TYPE = "SLACK_THREAD";
    private static final String KEY_PREFIX = "slack_thread:";
    private static final long TTL_MINUTES = 60;

    private final AgentMemoryRepository memory;
    private final ObjectMapper mapper = new ObjectMapper();

    public SlackConversationStore(AgentMemoryRepository memory) {
        this.memory = memory;
    }

    public String threadKey(String channel, String threadTs) {
        String suffix = threadTs == null || threadTs.isBlank() ? channel : threadTs;
        return KEY_PREFIX + (suffix == null ? "" : suffix);
    }

    @Transactional(readOnly = true)
    public Optional<SlackTurnContext> load(String threadKey) {
        if (threadKey == null) return Optional.empty();
        List<AgentMemory> rows = memory.findByMemoryTypeAndMemKey(MEMORY_TYPE, threadKey);
        for (AgentMemory r : rows) {
            if (r.getExpiresAt() != null && r.getExpiresAt().isBefore(LocalDateTime.now())) continue;
            try {
                return Optional.of(mapper.readValue(r.getMemValue(), SlackTurnContext.class));
            } catch (Exception e) {
                log.warn("SlackConversationStore could not parse memory {}: {}", r.getId(), e.getMessage());
            }
        }
        return Optional.empty();
    }

    @Transactional
    public void save(String threadKey, SlackTurnContext ctx) {
        if (threadKey == null || ctx == null) return;
        String json;
        try { json = mapper.writeValueAsString(ctx); }
        catch (Exception e) { log.warn("SlackConversationStore serialize failed: {}", e.getMessage()); return; }

        AgentMemory row = new AgentMemory();
        row.setMemoryType(MEMORY_TYPE);
        row.setMemKey(threadKey);
        row.setMemValue(json);
        row.setCreatedAt(LocalDateTime.now());
        row.setExpiresAt(LocalDateTime.now().plusMinutes(TTL_MINUTES));
        memory.save(row);
    }

    /**
     * Snapshot of the last successful turn. Kept intentionally small — only the fields
     * we want to inherit into the next turn's intent.
     */
    public record SlackTurnContext(String lastTool,
                                   String projectName,
                                   String severity,
                                   Long lastRunId) {
        public Map<String, Object> inheritableArgs() {
            Map<String, Object> args = new LinkedHashMap<>();
            if (projectName != null && !projectName.isBlank()) args.put("projectName", projectName);
            if (severity != null && !severity.isBlank()) args.put("severity", severity);
            return args;
        }
    }
}
