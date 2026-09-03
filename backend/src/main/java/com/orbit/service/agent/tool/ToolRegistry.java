package com.orbit.service.agent.tool;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ToolRegistry {

    private final Map<String, AgentTool> tools;

    public ToolRegistry(List<AgentTool> toolList) {
        this.tools = toolList.stream()
            .collect(Collectors.toMap(AgentTool::id, t -> t));
    }

    public Optional<AgentTool> find(String id) {
        return Optional.ofNullable(tools.get(id));
    }

    public List<Map<String, Object>> listAll() {
        return tools.values().stream()
            .map(t -> Map.<String, Object>of(
                "id", t.id(),
                "description", t.description(),
                "requiresHitl", t.requiresHitl()
            ))
            .collect(Collectors.toList());
    }
}
