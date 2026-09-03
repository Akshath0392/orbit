package com.orbit.service.agent.tool;

import com.orbit.repository.AgentMemoryRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MemoryReadTool implements AgentTool {

    private final AgentMemoryRepository memory;

    public MemoryReadTool(AgentMemoryRepository memory) {
        this.memory = memory;
    }

    @Override
    public String id() { return "memory.read"; }

    @Override
    public String description() { return "Read agent memory for current project"; }

    @Override
    public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        String key = (String) args.getOrDefault("key", "");
        return memory.findByAgentIdAndProjectIdAndMemKey(ctx.getAgentId(), ctx.getProjectId(), key)
            .map(m -> Map.<String, Object>of(
                "key", key,
                "value", m.getMemValue() != null ? m.getMemValue() : "",
                "found", true
            ))
            .orElse(Map.of("key", key, "found", false));
    }
}
