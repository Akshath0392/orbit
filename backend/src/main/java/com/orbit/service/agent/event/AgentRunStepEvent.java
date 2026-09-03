package com.orbit.service.agent.event;

import java.time.Instant;

/**
 * Published by {@link com.orbit.service.agent.AgentRuntime} at each tool-call boundary
 * (STARTED, COMPLETED, AWAITING_HITL, FAILED) so live consumers — most notably the
 * Admin → Agents → Detail → Live tab — can tail run progress over STOMP.
 *
 * Fire-and-forget: listeners must tolerate transport outages without rolling back
 * the run (mirrors {@link HitlAwaitingEvent}).
 *
 * @param runId     AgentRun.id
 * @param stepId    AgentToolCall.id (may be null if not yet persisted)
 * @param toolName  tool identifier (e.g. "slack.send_channel", "report.draft")
 * @param status    one of STARTED | COMPLETED | AWAITING_HITL | FAILED
 * @param message   short human-readable line ("posted to #orbit-hitl", error msg, ...)
 * @param ts        event timestamp
 */
public record AgentRunStepEvent(Long runId,
                                Long stepId,
                                String toolName,
                                String status,
                                String message,
                                Instant ts) {

    public static AgentRunStepEvent started(Long runId, Long stepId, String toolName) {
        return new AgentRunStepEvent(runId, stepId, toolName, "STARTED", null, Instant.now());
    }

    public static AgentRunStepEvent completed(Long runId, Long stepId, String toolName, String message) {
        return new AgentRunStepEvent(runId, stepId, toolName, "COMPLETED", message, Instant.now());
    }

    public static AgentRunStepEvent awaitingHitl(Long runId, Long stepId, String toolName) {
        return new AgentRunStepEvent(runId, stepId, toolName, "AWAITING_HITL", null, Instant.now());
    }

    public static AgentRunStepEvent failed(Long runId, Long stepId, String toolName, String error) {
        return new AgentRunStepEvent(runId, stepId, toolName, "FAILED", error, Instant.now());
    }
}
