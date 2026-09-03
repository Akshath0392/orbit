package com.orbit.service.agent;

import java.util.Map;

/**
 * Outcome of a single agent invocation routed through {@link AgentInvocationService}.
 * Used uniformly for native @Service agents and AgentRuntime-driven definitions.
 */
public record AgentInvocationResult(
    Long runId,
    String agentKey,
    String status,
    String summary,
    Map<String, Object> outputs
) {
    public static AgentInvocationResult completed(Long runId, String agentKey, String summary, Map<String, Object> outputs) {
        return new AgentInvocationResult(runId, agentKey, "COMPLETED", summary, outputs == null ? Map.of() : outputs);
    }

    public static AgentInvocationResult failed(Long runId, String agentKey, String error) {
        return new AgentInvocationResult(runId, agentKey, "FAILED", error, Map.of("error", error));
    }
}
