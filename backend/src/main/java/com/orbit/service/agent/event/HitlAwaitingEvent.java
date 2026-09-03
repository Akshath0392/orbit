package com.orbit.service.agent.event;

/**
 * Published by {@link com.orbit.service.agent.AgentRuntime} whenever a tool call
 * is recorded as {@code AWAITING_HITL}. Slack and other surfaces listen and
 * post approval prompts; the event is fire-and-forget — listeners must tolerate
 * Slack-not-configured / channel-missing without rolling back the run.
 *
 * @param runId        AgentRun.id holding the awaiting step
 * @param stepId       AgentToolCall.id (the pending step)
 * @param agentName    human-readable name (AgentDefinition.name or native key)
 * @param toolName     the HITL-gated tool that's awaiting decision
 * @param argsJson     captured input args (JSON string) — what the approver sees
 * @param triggeredBy  who initiated the run (email / "system" / "CRON")
 */
public record HitlAwaitingEvent(Long runId,
                                Long stepId,
                                String agentName,
                                String toolName,
                                String argsJson,
                                String triggeredBy) {}
