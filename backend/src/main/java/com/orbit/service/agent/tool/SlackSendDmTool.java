package com.orbit.service.agent.tool;

import com.orbit.integration.slack.SlackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SlackSendDmTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SlackSendDmTool.class);
    private final SlackService slack;

    public SlackSendDmTool(SlackService slack) { this.slack = slack; }

    @Override public String id()            { return "slack.send_dm"; }
    @Override public String description()   { return "Send a Slack direct message to a user by email"; }
    @Override public boolean requiresHitl() { return true; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        String email   = String.valueOf(args.getOrDefault("email", ""));
        String message = String.valueOf(args.getOrDefault("message", ""));

        if (email.isBlank()) {
            return Map.of("ok", false, "error", "no_email_specified");
        }
        if (message.isBlank()) {
            return Map.of("ok", false, "error", "no_message_specified");
        }

        return slack.resolveSlackUserId(email)
            .map(userId -> {
                boolean sent = slack.sendDm(userId, message);
                if (sent) {
                    log.info("slack.send_dm: sent DM to {} (userId={})", email, userId);
                    return Map.<String, Object>of("ok", true, "email", email, "slackUserId", userId);
                } else {
                    log.warn("slack.send_dm: Slack API rejected DM to {}", email);
                    return Map.<String, Object>of("ok", false, "error", "slack_api_error", "email", email);
                }
            })
            .orElseGet(() -> {
                log.warn("slack.send_dm: could not resolve Slack user ID for {}", email);
                return Map.of("ok", false, "error", "user_not_found", "email", email);
            });
    }
}
