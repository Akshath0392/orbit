package com.orbit.service.agent.tool;

import java.util.Map;

public interface AgentTool {
    String id();
    String description();
    boolean requiresHitl();
    Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx);
}
