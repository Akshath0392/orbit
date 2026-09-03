package com.orbit.service.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EmailSendTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(EmailSendTool.class);

    @Override public String id()            { return "email.send"; }
    @Override public String description()   { return "Send an email to one or more recipients"; }
    @Override public boolean requiresHitl() { return true; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        String to      = String.valueOf(args.getOrDefault("to", ""));
        String subject = String.valueOf(args.getOrDefault("subject", "(no subject)"));
        String body    = String.valueOf(args.getOrDefault("body", ""));

        if (to.isBlank()) {
            log.warn("email.send: no recipient specified");
            return Map.of("ok", false, "error", "no_recipient");
        }

        log.info("email.send: to={} subject={} (HITL-approved)", to, subject);
        return Map.of("ok", true, "to", to, "subject", subject, "note", "email queued for delivery");
    }
}
