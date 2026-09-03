package com.orbit.integration.slack;

import com.orbit.domain.config.SlackConfig;
import com.orbit.repository.SlackConfigRepository;
import com.orbit.service.agent.event.HitlAwaitingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bridges {@link HitlAwaitingEvent} → Slack approval card posted to the default
 * channel from the admin Slack config. Async + best-effort: a missing channel /
 * Slack outage must NEVER roll back the agent run.
 */
@Component
public class SlackHitlBridge {

    private static final Logger log = LoggerFactory.getLogger(SlackHitlBridge.class);

    private final SlackConfigRepository configs;
    private final SlackClient slack;
    private final SlackResponseRenderer renderer;
    private final String orbitBase;

    public SlackHitlBridge(SlackConfigRepository configs,
                           SlackClient slack,
                           SlackResponseRenderer renderer,
                           @Value("${orbit.base-url:}") String orbitBase) {
        this.configs = configs;
        this.slack = slack;
        this.renderer = renderer;
        this.orbitBase = orbitBase;
    }

    @Async
    @EventListener
    public void onHitlAwaiting(HitlAwaitingEvent ev) {
        Optional<SlackConfig> cfg = configs.findFirstByEnabledTrue();
        if (cfg.isEmpty() || cfg.get().getDefaultChannel() == null || cfg.get().getDefaultChannel().isBlank()) {
            log.info("HITL approval skipped — Slack not configured or no default channel (runId={})", ev.runId());
            return;
        }
        String channel = cfg.get().getDefaultChannel();
        List<Map<String, Object>> blocks = renderer.hitlApprovalCard(
            ev.runId(), ev.stepId(), ev.agentName(), ev.toolName(), ev.argsJson(),
            ev.triggeredBy(), orbitBase);
        String fallback = "Orbit · HITL approval needed for " + ev.toolName();
        try {
            Map<String, Object> resp = slack.postMessage(channel, fallback, blocks);
            if (!Boolean.TRUE.equals(resp.get("ok"))) {
                log.warn("HITL card post failed: runId={} error={}", ev.runId(), resp.get("error"));
            } else {
                log.info("HITL card posted: runId={} stepId={} channel={} ts={}",
                    ev.runId(), ev.stepId(), channel, resp.get("ts"));
            }
        } catch (RuntimeException e) {
            log.warn("HITL card post threw: runId={} error={}", ev.runId(), e.getMessage());
        }
    }
}
