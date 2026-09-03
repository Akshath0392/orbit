package com.orbit.service.sync;

import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.service.ai.AiGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * GPT-4o few-shot classifier that suggests severity (P0-P3) and an owner name
 * for incoming Jira issues.  Results are stored as {@code bert_suggested_*} fields
 * on the issue entity for PJM review via the {@code AiSuggestionChip} component.
 *
 * <p>The method is {@link Async} — it must not block the webhook response path.
 */
@Service
public class BertTriageService {

    private static final Logger log = LoggerFactory.getLogger(BertTriageService.class);

    private final AiGatewayService ai;
    private final JiraIssueRepository jiraIssueRepository;

    public BertTriageService(AiGatewayService ai, JiraIssueRepository jiraIssueRepository) {
        this.ai = ai;
        this.jiraIssueRepository = jiraIssueRepository;
    }

    /**
     * Classify a Jira issue using GPT-4o few-shot prompting.
     * Sets {@code bertSuggestedSeverity}, {@code bertSuggestedOwner}, and
     * {@code bertSuggestionAccepted=null} (pending PJM review) on the issue and saves it.
     *
     * @param issue the issue to classify; must already be persisted
     */
    @Async
    public void classifyIssue(JiraIssue issue) {
        if (issue == null || issue.getId() == null) {
            log.warn("BertTriageService: classifyIssue called with null/unsaved issue — skipping");
            return;
        }

        String summary = issue.getSummary() != null ? issue.getSummary() : "";
        log.info("BertTriageService: Classifying issue {} — '{}'",
                issue.getIssueKey(), truncate(summary, 80));

        try {
            String systemPrompt = buildSystemPrompt();
            String userMessage = buildUserMessage(issue);

            String response = ai.complete(systemPrompt, userMessage);

            String severity = parseSeverity(response);
            String owner = parseOwner(response);

            issue.setBertSuggestedSeverity(severity);
            issue.setBertSuggestedOwner(owner);
            issue.setBertSuggestionAccepted(null); // awaiting PJM review

            jiraIssueRepository.save(issue);

            log.info("BertTriageService: Issue {} classified — severity={} owner='{}'",
                    issue.getIssueKey(), severity, owner);

        } catch (Exception e) {
            log.error("BertTriageService: Classification failed for issue {} — {}",
                    issue.getIssueKey(), e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Prompt construction
    // -------------------------------------------------------------------------

    private String buildSystemPrompt() {
        return """
                You are a severity and owner classification assistant for software project issues.
                Given an issue summary and type, output EXACTLY two lines and nothing else:
                SEVERITY: <P0|P1|P2|P3>
                OWNER: <first-name.last-name or "unassigned">

                Severity guidelines:
                P0 — production down, data loss, security breach
                P1 — major feature broken, SLA at risk, critical client impact
                P2 — significant defect, workaround exists, moderate impact
                P3 — minor defect, cosmetic, low business impact

                Owner guidelines — use the canonical team member names:
                Kavya T. (kavya.t) — production bugs, P0/P1 priority
                Arjun Kumar (arjun.kumar) — CR delivery, integration issues
                Rahul V. (rahul.v) — UAT bugs, QA issues
                Rajan M. (rajan.m) — hold/blocked issues, client coordination
                Dev Lal (dev.lal) — capacity and planning issues
                unassigned — when no clear match

                Few-shot examples:
                Issue: "Login page throws 500 error for all users after deployment" | Type: Bug
                SEVERITY: P0
                OWNER: kavya.t

                Issue: "Export to Excel button missing on CR detail view" | Type: Bug
                SEVERITY: P3
                OWNER: rahul.v

                Issue: "Add multi-currency support to invoicing module" | Type: CR
                SEVERITY: P1
                OWNER: arjun.kumar

                Issue: "CR-884 on hold 18 days — no owner, all milestones TBC" | Type: CR
                SEVERITY: P1
                OWNER: rajan.m

                Issue: "Minor typo in dashboard tooltip text" | Type: Bug
                SEVERITY: P3
                OWNER: rahul.v
                """;
    }

    private String buildUserMessage(JiraIssue issue) {
        StringBuilder sb = new StringBuilder();
        sb.append("Issue: \"").append(issue.getSummary() != null ? issue.getSummary() : "(no summary)").append("\"");
        sb.append(" | Type: ").append(issue.getIssueType() != null ? issue.getIssueType() : "Unknown");
        if (issue.getClient() != null && issue.getClient().getName() != null) {
            sb.append(" | Client: ").append(issue.getClient().getName());
        }
        if (issue.getPriority() != null) {
            sb.append(" | Jira Priority: ").append(issue.getPriority());
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    /**
     * Extracts the P0/P1/P2/P3 label from the LLM response.
     * Scans lines for "SEVERITY:" prefix; falls back to regex scan of the full text.
     */
    private String parseSeverity(String response) {
        if (response == null) return "P2";

        for (String line : response.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.toUpperCase().startsWith("SEVERITY:")) {
                String value = trimmed.substring("SEVERITY:".length()).trim().toUpperCase();
                if (value.startsWith("P0")) return "P0";
                if (value.startsWith("P1")) return "P1";
                if (value.startsWith("P2")) return "P2";
                if (value.startsWith("P3")) return "P3";
            }
        }

        // Fallback: scan anywhere in response
        if (response.contains("P0")) return "P0";
        if (response.contains("P1")) return "P1";
        if (response.contains("P3")) return "P3";
        return "P2"; // safe default
    }

    /**
     * Extracts the owner name from the LLM response.
     * Scans lines for "OWNER:" prefix; returns "unassigned" if not found or not parseable.
     */
    private String parseOwner(String response) {
        if (response == null) return "unassigned";

        for (String line : response.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.toUpperCase().startsWith("OWNER:")) {
                String value = trimmed.substring("OWNER:".length()).trim();
                if (!value.isBlank()) return value;
            }
        }
        return "unassigned";
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
