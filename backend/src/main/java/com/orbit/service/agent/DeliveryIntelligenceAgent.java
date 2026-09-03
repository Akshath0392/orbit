package com.orbit.service.agent;

import com.orbit.domain.agent.AgentDecisionLog;
import com.orbit.domain.alert.Alert;
import com.orbit.repository.AgentDecisionLogRepository;
import com.orbit.repository.AlertRepository;
import com.orbit.service.ai.AiGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class DeliveryIntelligenceAgent {

    private static final Logger log = LoggerFactory.getLogger(DeliveryIntelligenceAgent.class);

    private static final String AGENT_NAME = "DeliveryIntelligenceAgent";
    private static final String BRIEFING_TOPIC = "/topic/copilot/daily-briefing";

    private final AiGateway ai;
    private final SimpMessagingTemplate ws;
    private final AlertRepository alertRepository;
    private final AgentDecisionLogRepository decisionLogRepository;

    public DeliveryIntelligenceAgent(AiGateway ai,
                                     SimpMessagingTemplate ws,
                                     AlertRepository alertRepository,
                                     AgentDecisionLogRepository decisionLogRepository) {
        this.ai = ai;
        this.ws = ws;
        this.alertRepository = alertRepository;
        this.decisionLogRepository = decisionLogRepository;
    }

    /**
     * Scheduled daily briefing — Mon-Fri at 8am.
     */
    @Scheduled(cron = "${orbit.agents.delivery.cron:0 0 8 * * MON-FRI}")
    public void runDailyBriefing() {
        log.info("{}: Running scheduled daily briefing", AGENT_NAME);
        CompletableFuture.runAsync(() -> executeRun("Daily 8am scheduled briefing"));
    }

    /**
     * Webhook-triggered run for a specific Jira issue event.
     *
     * @param issueKey the Jira issue key that triggered the webhook
     */
    public void onIssueEvent(String issueKey) {
        log.info("{}: Triggered by webhook event for issue {}", AGENT_NAME, issueKey);
        CompletableFuture.runAsync(() -> executeRun("Jira webhook event: " + issueKey));
    }

    // -------------------------------------------------------------------------
    // Core logic
    // -------------------------------------------------------------------------

    private void executeRun(String triggerEvent) {
        try {
            // 1. Load top 5 open alerts
            List<Alert> openAlerts = alertRepository.findTop5ByStatusOrderByCreatedAtDesc("OPEN");

            // 2. Build context for the LLM
            String systemPrompt = buildSystemPrompt();
            String userMessage = buildUserMessage(openAlerts);

            // 3. Call AI
            String response = ai.complete(systemPrompt, userMessage);

            // 4. Stream response to WS topic word-by-word
            streamTokens(BRIEFING_TOPIC, response);

            // 5. For each CRITICAL alert create a decision log entry + emit proposal
            for (Alert alert : openAlerts) {
                if ("CRITICAL".equalsIgnoreCase(alert.getSeverity())) {
                    emitEscalationProposal(alert, triggerEvent);
                }
            }

            sendDone(BRIEFING_TOPIC);

        } catch (Exception e) {
            log.error("{}: Execution failed — {}", AGENT_NAME, e.getMessage(), e);
            sendDone(BRIEFING_TOPIC);
        }
    }

    private void streamTokens(String topic, String response) {
        String[] words = response.split(" ");
        for (String word : words) {
            LinkedHashMap<String, Object> event = new LinkedHashMap<>();
            event.put("type", "token");
            event.put("content", word + " ");
            ws.convertAndSend(topic, event);
            try {
                Thread.sleep(40);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void sendDone(String topic) {
        LinkedHashMap<String, Object> done = new LinkedHashMap<>();
        done.put("type", "done");
        ws.convertAndSend(topic, done);
    }

    private void emitEscalationProposal(Alert alert, String triggerEvent) {
        String proposalJson = buildProposalJson(alert);

        AgentDecisionLog decision = AgentDecisionLog.builder()
                .agentName(AGENT_NAME)
                .triggerEvent(triggerEvent)
                .proposalJson(proposalJson)
                .outcome(null)
                .tokensUsed(estimateTokens(proposalJson))
                .decidedAt(LocalDateTime.now())
                .build();
        decisionLogRepository.save(decision);

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("alertId", alert.getId());
        payload.put("title", alert.getTitle());
        payload.put("severity", alert.getSeverity());

        LinkedHashMap<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("type", "proposal");
        proposal.put("id", decision.getId());
        proposal.put("action", "escalate");
        proposal.put("payload", payload);

        ws.convertAndSend(BRIEFING_TOPIC, proposal);

        log.info("{}: Emitted escalation proposal id={} for alert id={}", AGENT_NAME, decision.getId(), alert.getId());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildSystemPrompt() {
        return """
                You are Gauge Delivery Intelligence Agent, an AI delivery assistant for project managers.
                Your job is to analyze open project alerts and provide a concise daily briefing.
                Focus on: critical SLA breaches, stalled CRs, overloaded team members, and slip probability.
                Be specific, reference issue keys and client names, and recommend immediate actions.
                Keep the briefing to 3-5 bullet points.
                """;
    }

    private String buildUserMessage(List<Alert> alerts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Daily delivery briefing request. Current open alerts:\n\n");
        if (alerts.isEmpty()) {
            sb.append("No open alerts at this time.\n");
        } else {
            for (Alert a : alerts) {
                sb.append("- [").append(a.getSeverity()).append("] ")
                  .append(a.getTitle() != null ? a.getTitle() : "(no title)");
                if (a.getDetail() != null) {
                    sb.append(": ").append(a.getDetail());
                }
                sb.append("\n");
            }
        }
        sb.append("\nProvide a prioritised briefing with recommended actions for the PJM.");
        return sb.toString();
    }

    private String buildProposalJson(Alert alert) {
        return "{\"action\":\"escalate\","
                + "\"alertId\":" + alert.getId() + ","
                + "\"title\":\"" + escapeJson(alert.getTitle()) + "\","
                + "\"severity\":\"" + alert.getSeverity() + "\","
                + "\"detail\":\"" + escapeJson(alert.getDetail()) + "\"}";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /** Rough token estimate: word count * 2 (matches CopilotController convention). */
    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.split("\\s+").length * 2;
    }
}
