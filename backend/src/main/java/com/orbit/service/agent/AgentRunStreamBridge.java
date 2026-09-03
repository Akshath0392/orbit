package com.orbit.service.agent;

import com.orbit.service.agent.event.AgentRunStepEvent;
import com.orbit.service.agent.event.HitlAwaitingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Bridges agent-run events to the STOMP topic {@code /topic/agent-runs/{runId}} so the
 * Admin → Agents → Detail → Live tab can tail a run as it executes.
 *
 * Listens to both {@link AgentRunStepEvent} (every tool boundary) and {@link HitlAwaitingEvent}
 * (republished as an {@code AWAITING_HITL} step so the Live tab gets the event without
 * polling the HITL inbox).
 *
 * Async + best-effort: a WS outage must NEVER roll back the agent run. Errors are logged
 * and swallowed.
 */
@Component
public class AgentRunStreamBridge {

    private static final Logger log = LoggerFactory.getLogger(AgentRunStreamBridge.class);
    private static final String TOPIC_PREFIX = "/topic/agent-runs/";

    private final SimpMessagingTemplate ws;

    public AgentRunStreamBridge(SimpMessagingTemplate ws) {
        this.ws = ws;
    }

    @Async
    @EventListener
    public void onStep(AgentRunStepEvent ev) {
        if (ev == null || ev.runId() == null) return;
        try {
            ws.convertAndSend(TOPIC_PREFIX + ev.runId(), ev);
        } catch (RuntimeException e) {
            log.warn("AgentRunStreamBridge forward failed runId={} status={}: {}",
                ev.runId(), ev.status(), e.getMessage());
        }
    }

    @Async
    @EventListener
    public void onHitlAwaiting(HitlAwaitingEvent ev) {
        if (ev == null || ev.runId() == null) return;
        AgentRunStepEvent forwarded = new AgentRunStepEvent(
            ev.runId(), ev.stepId(), ev.toolName(),
            "AWAITING_HITL",
            "HITL approval required (triggered by " + ev.triggeredBy() + ")",
            Instant.now());
        try {
            ws.convertAndSend(TOPIC_PREFIX + ev.runId(), forwarded);
        } catch (RuntimeException e) {
            log.warn("AgentRunStreamBridge hitl forward failed runId={}: {}", ev.runId(), e.getMessage());
        }
    }
}
