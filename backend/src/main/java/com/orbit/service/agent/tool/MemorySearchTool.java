package com.orbit.service.agent.tool;

import com.orbit.repository.AgentMemoryRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MemorySearchTool implements AgentTool {

    private final AgentMemoryRepository memory;

    public MemorySearchTool(AgentMemoryRepository memory) { this.memory = memory; }

    @Override public String id()            { return "memory.search"; }
    @Override public String description()   { return "Search the agent's long-term memory by keyword"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        String query = String.valueOf(args.getOrDefault("query", "")).toLowerCase().trim();
        if (query.isBlank()) return Map.of("results", List.of(), "query", "");

        Long agentId   = ctx != null ? ctx.getAgentId()   : null;
        Long projectId = ctx != null ? ctx.getProjectId() : null;

        List<Map<String, Object>> results = memory.findByAgentIdAndProjectId(agentId, projectId)
            .stream()
            .filter(m -> m.getMemValue() != null && m.getMemValue().toLowerCase().contains(query))
            .limit(5)
            .map(m -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("key", m.getMemKey());
                r.put("value", m.getMemValue());
                r.put("type", m.getMemoryType());
                r.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
                return r;
            }).collect(Collectors.toList());

        return Map.of(
            "query", query,
            "results", results,
            "count", results.size(),
            "note", "keyword match — pgvector semantic search available after EmbeddingService is wired"
        );
    }
}
