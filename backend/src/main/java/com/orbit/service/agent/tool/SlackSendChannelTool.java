package com.orbit.service.agent.tool;

import com.orbit.integration.slack.SlackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SlackSendChannelTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SlackSendChannelTool.class);

    private final SlackService slack;

    public SlackSendChannelTool(SlackService slack) {
        this.slack = slack;
    }

    @Override public String id()            { return "slack.send_channel"; }
    @Override public String description()   { return "Post a message to the project's configured Slack channel"; }
    // Outbound + public (a channel broadcast reaches more people than a DM), and its
    // content can be sourced from injectable Jira/Slack text — so it requires HITL
    // approval like the other outbound tools (audit M2, upholds locked rule 3).
    @Override public boolean requiresHitl() { return true; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        // Resolve channel: args override → project mapping → default channel from config
        String channel = resolveChannel(args, ctx);

        if (channel == null || channel.isBlank()) {
            log.warn("slack.send_channel: no channel resolved (projectId={}) — configure a channel mapping in Integrations > Slack",
                ctx != null ? ctx.getProjectId() : "null");
            return Map.of("ok", false, "error", "no_channel_configured",
                "hint", "Configure a Slack channel mapping in Integrations > Slack > Project channel mapping");
        }

        String message = buildMessage(args, ctx);
        Map<String, Object> result = slack.sendToChannelDetailed(channel, message);

        if (!Boolean.TRUE.equals(result.get("ok"))) {
            log.warn("slack.send_channel: Slack rejected message to {} — error: {}", channel, result.get("error"));
        }
        return result;
    }

    private String resolveChannel(Map<String, Object> args, AgentRunContext ctx) {
        // 1. Explicit channel from args (used when caller provides one)
        Object argChannel = args.get("channel");
        if (argChannel instanceof String s && !s.isBlank() && !s.equals("general")) {
            return s;
        }
        // 2. Project-specific channel mapping or default channel from Slack config
        Long projectId = ctx != null ? ctx.getProjectId() : null;
        return slack.resolveChannel(projectId).orElse(null);
    }

    private String buildMessage(Map<String, Object> args, AgentRunContext ctx) {
        Object argMessage = args.get("message");
        if (argMessage instanceof String s && !s.isBlank()) {
            return s;
        }
        // Default: standup-style ping from the agent
        String agentName = ctx != null && ctx.getAgentId() != null
            ? "Agent #" + ctx.getAgentId()
            : "Orbit agent";
        return String.format("[%s] Orbit agent run triggered. Check the Orbit dashboard for the latest project status.", agentName);
    }
}
