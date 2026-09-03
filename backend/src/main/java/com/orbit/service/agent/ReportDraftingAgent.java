package com.orbit.service.agent;

import com.orbit.domain.agent.AgentDecisionLog;
import com.orbit.domain.alert.Alert;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.domain.report.GeneratedReport;
import com.orbit.repository.AgentDecisionLogRepository;
import com.orbit.repository.AlertRepository;
import com.orbit.repository.GeneratedReportRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.ManDayBudgetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.orbit.service.ai.AiGateway;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class ReportDraftingAgent {

    private static final Logger log = LoggerFactory.getLogger(ReportDraftingAgent.class);

    private static final String AGENT_NAME = "ReportDraftingAgent";

    private final AiGateway ai;
    private final SimpMessagingTemplate ws;
    private final GeneratedReportRepository reportRepository;
    private final AlertRepository alertRepository;
    private final JiraIssueRepository jiraIssueRepository;
    private final ManDayBudgetRepository manDayBudgetRepository;
    private final AgentDecisionLogRepository decisionLogRepository;

    public ReportDraftingAgent(AiGateway ai,
                               SimpMessagingTemplate ws,
                               GeneratedReportRepository reportRepository,
                               AlertRepository alertRepository,
                               JiraIssueRepository jiraIssueRepository,
                               ManDayBudgetRepository manDayBudgetRepository,
                               AgentDecisionLogRepository decisionLogRepository) {
        this.ai = ai;
        this.ws = ws;
        this.reportRepository = reportRepository;
        this.alertRepository = alertRepository;
        this.jiraIssueRepository = jiraIssueRepository;
        this.manDayBudgetRepository = manDayBudgetRepository;
        this.decisionLogRepository = decisionLogRepository;
    }

    /**
     * Async: Draft the content for a generated report and persist the result.
     * Emits a {@code report_ready} WS event when done.
     *
     * @param reportId the ID of the {@link GeneratedReport} to populate
     * @param userId   the authenticated user's name — used to address the WS event
     */
    @Async
    public void draftReport(Long reportId, String userId) {
        log.info("{}: Starting draft for reportId={} user={}", AGENT_NAME, reportId, userId);

        GeneratedReport report = reportRepository.findById(reportId).orElse(null);
        if (report == null) {
            log.warn("{}: Report id={} not found", AGENT_NAME, reportId);
            return;
        }

        try {
            // 1. Build context
            Long clientId = report.getClient() != null ? report.getClient().getId() : null;
            String context = buildContext(report, clientId);

            // 2. Call AI with a structured prompt
            String systemPrompt = buildSystemPrompt();
            String response = ai.complete(systemPrompt, context);

            // 3. Parse into 4 sections
            String contentJson = parseSections(response, report.getType());

            // 4. Update report
            report.setContentJson(contentJson);
            report.setStatus("DONE");
            reportRepository.save(report);

            // 5. Store decision log
            AgentDecisionLog decision = AgentDecisionLog.builder()
                    .agentName(AGENT_NAME)
                    .triggerEvent("Report generation: id=" + reportId + " type=" + report.getType())
                    .proposalJson("{\"reportId\":" + reportId + ",\"type\":\"" + escapeJson(report.getType()) + "\"}")
                    .outcome(null)
                    .tokensUsed(estimateTokens(response))
                    .decidedAt(LocalDateTime.now())
                    .build();
            decisionLogRepository.save(decision);

            // 6. Emit report_ready WS event
            LinkedHashMap<String, Object> event = new LinkedHashMap<>();
            event.put("type", "report_ready");
            event.put("reportId", reportId);
            event.put("title", report.getType());
            ws.convertAndSend("/topic/reports/" + userId, event);

            log.info("{}: Report id={} completed and WS event sent to user={}", AGENT_NAME, reportId, userId);

        } catch (Exception e) {
            log.error("{}: Failed to draft report id={} — {}", AGENT_NAME, reportId, e.getMessage(), e);
            report.setStatus("ERROR");
            reportRepository.save(report);
        }
    }

    // -------------------------------------------------------------------------
    // Context builder
    // -------------------------------------------------------------------------

    private String buildContext(GeneratedReport report, Long clientId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Report type: ").append(report.getType()).append("\n");
        if (report.getClient() != null) {
            sb.append("Client: ").append(report.getClient().getName()).append("\n");
        }
        if (report.getManualNotes() != null && !report.getManualNotes().isBlank()) {
            sb.append("PJM notes: ").append(report.getManualNotes()).append("\n");
        }
        sb.append("\n");

        // 3 recent open alerts for this client (or global if no client)
        List<Alert> alerts = alertRepository
                .findFiltered("CRITICAL", "OPEN", clientId, PageRequest.of(0, 3))
                .getContent();
        if (alerts.isEmpty()) {
            alerts = alertRepository
                    .findFiltered(null, "OPEN", clientId, PageRequest.of(0, 3))
                    .getContent();
        }
        sb.append("## Recent Alerts (up to 3)\n");
        if (alerts.isEmpty()) {
            sb.append("No open alerts.\n");
        } else {
            for (Alert a : alerts) {
                sb.append("- [").append(a.getSeverity()).append("] ")
                  .append(a.getTitle() != null ? a.getTitle() : "(no title)");
                if (a.getDetail() != null) sb.append(": ").append(a.getDetail());
                sb.append("\n");
            }
        }
        sb.append("\n");

        // 3 recent CRs for this client
        List<JiraIssue> crs;
        if (clientId != null) {
            crs = jiraIssueRepository
                    .findCrs(clientId, null, null, PageRequest.of(0, 3))
                    .getContent();
        } else {
            crs = jiraIssueRepository
                    .findCrs(null, null, null, PageRequest.of(0, 3))
                    .getContent();
        }
        sb.append("## CR Status (up to 3)\n");
        if (crs.isEmpty()) {
            sb.append("No open CRs.\n");
        } else {
            for (JiraIssue cr : crs) {
                sb.append("- [").append(cr.getIssueKey()).append("] ")
                  .append(cr.getSummary() != null ? cr.getSummary() : "(no summary)")
                  .append(" | Stage: ").append(cr.getLifecycleStage() != null ? cr.getLifecycleStage() : "Unknown")
                  .append(" | Priority: ").append(cr.getPriority() != null ? cr.getPriority() : "Unknown")
                  .append("\n");
            }
        }
        sb.append("\n");

        // Man-day summary for the project linked to the report (if available)
        sb.append("## Man-Day Summary\n");
        if (report.getProject() != null) {
            manDayBudgetRepository.findByProjectId(report.getProject().getId()).ifPresent(budget -> {
                sb.append("Project: ").append(report.getProject().getName()).append("\n");
                if (budget.getPurchasedDays() != null) {
                    sb.append("Purchased days: ").append(budget.getPurchasedDays()).append("\n");
                }
                sb.append("Alert threshold: ").append(budget.getAlertThresholdPct()).append("%\n");
            });
        } else {
            sb.append("No project-specific man-day data.\n");
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // System prompt + section parser
    // -------------------------------------------------------------------------

    private String buildSystemPrompt() {
        return """
                You are Gauge Report Drafting Agent. Write a project delivery status report with exactly 4 sections.
                Use these exact section headings on their own lines (with ## prefix):
                ## Executive Summary
                ## CR Status
                ## Bug Summary
                ## Capacity Risk
                Each section should be 2-4 sentences. Be specific, professional, and data-driven.
                Reference the provided context about alerts, CRs, and man-day data.
                """;
    }

    /**
     * Splits the LLM response on "## " headings.
     * Falls back to 4 stub sections if the model doesn't follow the format.
     */
    private String parseSections(String response, String reportType) {
        LinkedHashMap<String, String> sections = new LinkedHashMap<>();

        // Try to split on ## headings
        String[] parts = response.split("(?m)^##\\s+");
        if (parts.length >= 4) {
            for (int i = 1; i < parts.length && i <= 4; i++) {
                String part = parts[i].trim();
                int newlineIdx = part.indexOf('\n');
                String title = newlineIdx >= 0 ? part.substring(0, newlineIdx).trim() : part;
                String content = newlineIdx >= 0 ? part.substring(newlineIdx).trim() : "";
                sections.put(title, content);
            }
        }

        // If parsing failed or produced fewer than 4, use fixed section structure
        if (sections.size() < 4) {
            sections.clear();
            sections.put("Executive Summary", extractOrDefault(response, "Executive Summary",
                    "Report generated for " + reportType + ". Review the details below for current project status."));
            sections.put("CR Status", extractOrDefault(response, "CR Status",
                    "CR analysis based on current Jira data. See individual CR details for action items."));
            sections.put("Bug Summary", extractOrDefault(response, "Bug Summary",
                    "Bug triage summary based on open production and UAT issues."));
            sections.put("Capacity Risk", extractOrDefault(response, "Capacity Risk",
                    "Team capacity assessment based on current utilisation and leave data."));
        }

        return buildContentJson(sections);
    }

    /**
     * Tries to find a named section in the raw text; returns defaultText if not found.
     */
    private String extractOrDefault(String text, String heading, String defaultText) {
        int idx = text.indexOf(heading);
        if (idx < 0) return defaultText;
        int start = idx + heading.length();
        // Skip to the next line
        int newline = text.indexOf('\n', start);
        if (newline < 0) return text.substring(start).trim();
        // Read until the next ## heading or end
        int nextHeading = text.indexOf("##", newline);
        String content = nextHeading >= 0
                ? text.substring(newline, nextHeading).trim()
                : text.substring(newline).trim();
        return content.isBlank() ? defaultText : content;
    }

    private String buildContentJson(LinkedHashMap<String, String> sections) {
        StringBuilder json = new StringBuilder();
        json.append("{\"sections\":[");
        boolean first = true;
        for (java.util.Map.Entry<String, String> entry : sections.entrySet()) {
            if (!first) json.append(",");
            first = false;
            json.append("{\"title\":\"").append(escapeJson(entry.getKey())).append("\",");
            json.append("\"content\":\"").append(escapeJson(entry.getValue())).append("\"}");
        }
        json.append("]}");
        return json.toString();
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.split("\\s+").length * 2;
    }
}
