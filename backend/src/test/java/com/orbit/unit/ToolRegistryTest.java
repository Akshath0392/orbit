package com.orbit.unit;

import com.orbit.service.agent.tool.AgentRunContext;
import com.orbit.service.agent.tool.AgentTool;
import com.orbit.service.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private AgentTool fakeTool(String id, boolean hitl) {
        return new AgentTool() {
            public String id()          { return id; }
            public String description() { return "desc-" + id; }
            public boolean requiresHitl() { return hitl; }
            public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
                return Map.of("called", true);
            }
        };
    }

    @Test
    void findReturnsRegisteredTool() {
        ToolRegistry registry = new ToolRegistry(List.of(fakeTool("orbit.get_cr_summary", false)));
        assertTrue(registry.find("orbit.get_cr_summary").isPresent());
    }

    @Test
    void findReturnsEmptyForUnknownTool() {
        ToolRegistry registry = new ToolRegistry(List.of(fakeTool("orbit.get_cr_summary", false)));
        assertTrue(registry.find("unknown.tool").isEmpty());
    }

    @Test
    void listAllIncludesAllRegisteredTools() {
        ToolRegistry registry = new ToolRegistry(List.of(
            fakeTool("orbit.get_cr_summary", false),
            fakeTool("slack.send_channel", true),
            fakeTool("memory.read", false)
        ));
        assertEquals(3, registry.listAll().size());
    }

    @Test
    void listAllIncludesIdDescriptionAndHitlFlag() {
        ToolRegistry registry = new ToolRegistry(List.of(fakeTool("slack.send_channel", true)));
        Map<String, Object> entry = registry.listAll().get(0);
        assertEquals("slack.send_channel", entry.get("id"));
        assertEquals("desc-slack.send_channel", entry.get("description"));
        assertEquals(true, entry.get("requiresHitl"));
    }

    @Test
    void hitlFlagCorrectlyReflectsToolSetting() {
        ToolRegistry registry = new ToolRegistry(List.of(
            fakeTool("orbit.create_alert", false),
            fakeTool("email.send", true)
        ));
        assertFalse((Boolean) registry.find("orbit.create_alert").get().requiresHitl());
        assertTrue((Boolean)  registry.find("email.send").get().requiresHitl());
    }

    @Test
    void emptyRegistryListAllReturnsEmptyList() {
        ToolRegistry registry = new ToolRegistry(List.of());
        assertTrue(registry.listAll().isEmpty());
    }
}
