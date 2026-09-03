package com.orbit.service.agent;

import com.orbit.domain.agent.AgentDecisionLog;
import com.orbit.domain.alert.Alert;
import com.orbit.repository.AgentDecisionLogRepository;
import com.orbit.repository.AlertRepository;
import com.orbit.service.ai.AiGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

@Service
public class EscalationAgent {

    private static final Logger log = LoggerFactory.getLogger(EscalationAgent.class);

    private static final String AGENT_NAME = "EscalationAgent";
    private static final String DEFAULT_TOPIC = "/topic/copilot/default";

    /**
     * This flag must ALWAYS be true.
     * Escalations are NEVER sent automatically — human approval is mandatory.
     * Throwing on false is a safety guardrail; the config value should never be changed.
     */
    @Value("${orbit.agents.escalation.require-hitl:true}")
    private boolean requireHitl;

    private final AiGateway ai;
    private final SimpMessagingTemplate ws;
    private final AlertRepository alertRepository;
    private final AgentDecisionLogRepository decisionLogRepository;

    public EscalationAgent(AiGateway ai,
                           SimpMessagingTemplate ws,
                           AlertRepository alertRepository,
                           AgentDecisionLogRepository decisionLogRepository) {
        this.ai = ai;
        this.ws = ws;
        this.alertRepository = alertRepository;
        this.decisionLogRepository = decisionLogRepository;
    }

    /**
     * Trigger an escalation proposal for the given alert.
     * Drafts a Slack message via AI and emits a HITL proposal over WebSocket.
     * The actual notification is NEVER sent here — that requires explicit approval.
     *
     * @param alertId the ID of the alert to escalate
     * @throws IllegalStateException if requireHitl is somehow false (must never happen)
     */
    public void triggerEscalation(Long alertId) {
        if (!requireHitl) {
            throw new IllegalStateException(
                "EscalationAgent: orbit.agents.escalation.require-hitl must always be true. " +
                "Automatic escalation without human approval is forbidden.");
        }

        Alert alert = alertRepository.findById(alertId).orElse(null);
        if (alert == null) {
            log.warn("{}: Alert id={} not found — skipping escalation", AGENT_NAME, alertId);
            return;
        }

        log.info("{}: Drafting escalation for alert id={} severity={}", AGENT_NAME, alertId, alert.getSeverity());

        // 1. Build system + user prompts for Slack message draft
        String systemPrompt = buildSystemPrompt();
        String userMessage = buildUserMessage(alert);

        // 2. Call AI to draft the Slack message
        String draft = ai.complete(systemPrompt, userMessage);

        // 3. Persist the decision log entry — outcome null until HITL approval
        String proposalJson = buildProposalJson(alertId, alert, draft);
        AgentDecisionLog decision = AgentDecisionLog.builder()
                .agentName(AGENT_NAME)
                .triggerEvent("Alert escalation trigger: alertId=" + alertId)
                .proposalJson(proposalJson)
                .outcome(null)
                .tokensUsed(estimateTokens(draft))
                .decidedAt(LocalDateTime.now())
                .build();
        decisionLogRepository.save(decision);

        // 4. Emit WS proposal event for HITL review — send_notification NOT called here
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", "slack");
        payload.put("message", draft);
        payload.put("alertId", alertId);

        LinkedHashMap<String, Object> event = new LinkedHashMap<>();
        event.put("type", "proposal");
        event.put("id", decision.getId());
        event.put("action", "send_notification");
        event.put("payload", payload);

        ws.convertAndSend(DEFAULT_TOPIC, event);

        // 5. HITL gate — message is NOT sent. Log only.
        log.info("EscalationAgent: HITL pending — message not sent. Proposal id={}", decision.getId());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildSystemPrompt() {
        return """
                You are Gauge Escalation Agent. Draft a concise Slack escalation message for a project alert.
                The message should be addressed to the project stakeholder team.
                Include: alert severity, brief description of the issue, recommended action, and urgency.
                Keep it under 120 words. Use plain text — no markdown formatting for Slack.
                """;
    }

    private String buildUserMessage(Alert alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("Draft a Slack escalation message for the following alert:\n\n");
        sb.append("Severity: ").append(alert.getSeverity()).append("\n");
        sb.append("Title: ").append(alert.getTitle() != null ? alert.getTitle() : "(no title)").append("\n");
        if (alert.getDetail() != null && !alert.getDetail().isBlank()) {
            sb.append("Detail: ").append(alert.getDetail()).append("\n");
        }
        if (alert.getClient() != null && alert.getClient().getName() != null) {
            sb.append("Client: ").append(alert.getClient().getName()).append("\n");
        }
        return sb.toString();
    }

    private String buildProposalJson(Long alertId, Alert alert, String draft) {
        return "{\"action\":\"send_notification\","
                + "\"channel\":\"slack\","
                + "\"alertId\":" + alertId + ","
                + "\"severity\":\"" + alert.getSeverity() + "\","
                + "\"draft\":\"" + escapeJson(draft) + "\"}";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /** Rough token estimate: word count * 2 (matches project convention). */
    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.split("\\s+").length * 2;
    }
}
