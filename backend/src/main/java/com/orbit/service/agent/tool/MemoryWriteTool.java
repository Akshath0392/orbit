package com.orbit.service.agent.tool;

import com.orbit.domain.agent.AgentMemory;
import com.orbit.repository.AgentMemoryRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class MemoryWriteTool implements AgentTool {

    private final AgentMemoryRepository memory;

    public MemoryWriteTool(AgentMemoryRepository memory) {
        this.memory = memory;
    }

    @Override
    public String id() { return "memory.write"; }

    @Override
    public String description() { return "Persist a fact to agent memory for next run"; }

    @Override
    public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        String key = (String) args.get("key");
        String value = (String) args.get("value");
        if (key == null || key.isBlank()) {
            return Map.of("saved", false, "error", "key is required");
        }
        AgentMemory m = memory.findByAgentIdAndProjectIdAndMemKey(
                ctx.getAgentId(), ctx.getProjectId(), key)
            .orElse(new AgentMemory());
        m.setAgentId(ctx.getAgentId());
        m.setProjectId(ctx.getProjectId());
        m.setMemKey(key);
        m.setMemValue(value);
        m.setMemoryType("FACT");
        m.setCreatedAt(LocalDateTime.now());
        memory.save(m);
        return Map.of("saved", true, "key", key);
    }
}
