package com.orbit.integration.slack;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds Block Kit payloads for read-only Slack responses.
 * Each method returns a List of "blocks" suitable for chat.postMessage.
 * Renderer is pure (no I/O) — separates layout from transport so we can snapshot-test it.
 *
 * Block Kit reference: https://api.slack.com/block-kit
 */
@Component
public class SlackResponseRenderer {

    /** Empty-state card shown when a query returns no rows. */
    public List<Map<String, Object>> emptyState(String title, String message) {
        return List.of(
            header(title),
            section(":sparkles: " + message)
        );
    }

    /** Placeholder posted before kicking off an agent — replaced via chat.update when the run completes. */
    public List<Map<String, Object>> placeholder(String title) {
        return List.of(
            header(title),
            section(":hourglass_flowing_sand: Working on it… I'll edit this message with the result.")
        );
    }

    /** Compact alerts list. */
    public List<Map<String, Object>> alerts(List<AlertRow> rows) {
        if (rows == null || rows.isEmpty()) return emptyState("Alerts", "No matching alerts.");
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(header("Alerts (" + rows.size() + ")"));
        for (AlertRow a : rows) {
            String sevEmoji = switch (a.severity() == null ? "" : a.severity().toLowerCase()) {
                case "critical" -> ":red_circle:";
                case "warning"  -> ":large_orange_diamond:";
                default          -> ":small_blue_diamond:";
            };
            String text = sevEmoji + " *" + nz(a.title()) + "*"
                + (a.projectName() != null ? "  ·  " + a.projectName() : "")
                + (a.ageLabel() != null ? "  ·  " + a.ageLabel() : "");
            blocks.add(section(text));
        }
        blocks.add(context("Orbit · " + rows.size() + " alert" + (rows.size() == 1 ? "" : "s")));
        return blocks;
    }

    /** Bug-triage summary. */
    public List<Map<String, Object>> bugSummary(int p0, int p1, int p2, int p3, int slaBreached) {
        int total = p0 + p1 + p2 + p3;
        if (total == 0) return emptyState("Bug summary", "No open bugs.");
        return List.of(
            header("Bug summary (" + total + ")"),
            section(":bug: P0 *" + p0 + "*  ·  P1 *" + p1 + "*  ·  P2 *" + p2 + "*  ·  P3 *" + p3 + "*"
                + (slaBreached > 0 ? "\n:warning: " + slaBreached + " SLA-breached" : "")),
            context("Orbit · live count")
        );
    }

    /** Capacity strip showing per-team utilisation. */
    public List<Map<String, Object>> capacity(List<CapacityRow> rows) {
        if (rows == null || rows.isEmpty()) return emptyState("Capacity", "No team data available.");
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(header("Capacity"));
        for (CapacityRow r : rows) {
            String load = r.utilisationPct() + "%";
            String dot = r.utilisationPct() > 85 ? ":red_circle:"
                       : r.utilisationPct() > 70 ? ":large_orange_diamond:"
                       : ":large_green_circle:";
            blocks.add(section(dot + " *" + nz(r.team()) + "*  " + load
                + (r.headcount() > 0 ? "  ·  " + r.headcount() + " ppl" : "")));
        }
        return blocks;
    }

    /** Generic key-value card for fallback rendering. */
    public List<Map<String, Object>> keyValue(String title, Map<String, String> kv) {
        if (kv == null || kv.isEmpty()) return emptyState(title, "Nothing to show.");
        StringBuilder sb = new StringBuilder();
        for (var e : kv.entrySet()) {
            sb.append("*").append(e.getKey()).append("*: ").append(e.getValue()).append("\n");
        }
        return List.of(header(title), section(sb.toString().trim()));
    }

    /**
     * HITL approval card. Buttons carry action_id pattern {@code hitl:approve|reject|edit:<runId>:<stepId>}
     * so the interactivity router can dispatch without DB lookup.
     *
     * @param orbitBase root URL for the "Open in Orbit" link (e.g. https://orbit.example.com)
     */
    public List<Map<String, Object>> hitlApprovalCard(long runId, long stepId, String agentName,
                                                      String toolName, String argsJson,
                                                      String triggeredBy, String orbitBase) {
        String body = "*Agent:* " + nz(agentName)
            + "\n*Tool:* `" + nz(toolName) + "`"
            + "\n*Triggered by:* " + nz(triggeredBy)
            + "\n*Args:*\n```" + truncateForBlock(argsJson) + "```";
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(header(":hand: HITL approval needed"));
        blocks.add(section(body));
        blocks.add(actionsBlock(runId, stepId, orbitBase));
        return blocks;
    }

    /** Updates an approval card after a decision (replace_original via response_url). */
    public List<Map<String, Object>> hitlDecisionCard(String agentName, String toolName,
                                                      String outcome, String decidedBy, String note) {
        String emoji = switch (outcome == null ? "" : outcome) {
            case "APPROVED", "APPROVED_WITH_ERROR" -> ":white_check_mark:";
            case "REJECTED"                        -> ":x:";
            default                                -> ":grey_question:";
        };
        StringBuilder body = new StringBuilder()
            .append("*Agent:* ").append(nz(agentName))
            .append("\n*Tool:* `").append(nz(toolName)).append("`")
            .append("\n*Outcome:* ").append(emoji).append(" ").append(nz(outcome))
            .append("\n*Decided by:* ").append(nz(decidedBy));
        if (note != null && !note.isBlank()) body.append("\n*Note:* ").append(note);
        return List.of(header(":hand: HITL decision recorded"), section(body.toString()));
    }

    private Map<String, Object> actionsBlock(long runId, long stepId, String orbitBase) {
        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(button("Approve", "primary", "hitl:approve:" + runId + ":" + stepId, String.valueOf(stepId)));
        elements.add(button("Reject",  "danger",  "hitl:reject:"  + runId + ":" + stepId, String.valueOf(stepId)));
        elements.add(button("Edit & approve", null, "hitl:edit:" + runId + ":" + stepId, String.valueOf(stepId)));
        if (orbitBase != null && !orbitBase.isBlank()) {
            elements.add(linkButton("Open in Orbit", orbitBase + "/agents/runs/" + runId));
        }
        Map<String, Object> actions = new LinkedHashMap<>();
        actions.put("type", "actions");
        actions.put("block_id", "hitl_actions:" + runId + ":" + stepId);
        actions.put("elements", elements);
        return actions;
    }

    private Map<String, Object> button(String text, String style, String actionId, String value) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("type", "button");
        b.put("text", Map.of("type", "plain_text", "text", text, "emoji", true));
        if (style != null) b.put("style", style);
        b.put("action_id", actionId);
        b.put("value", value);
        return b;
    }

    private Map<String, Object> linkButton(String text, String url) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("type", "button");
        b.put("text", Map.of("type", "plain_text", "text", text, "emoji", true));
        b.put("url", url);
        b.put("action_id", "open_in_orbit");
        return b;
    }

    /**
     * Renders suggested next-action buttons appended to a result card. Each suggestion
     * becomes a button with {@code action_id="next:<tool>"} and {@code value=<argsJson>};
     * SlackInteractionRouter dispatches them as new turns.
     */
    public Map<String, Object> suggestionsBlock(List<Suggestion> suggestions) {
        List<Map<String, Object>> elements = new ArrayList<>();
        for (Suggestion s : suggestions) {
            elements.add(button(s.label(), null, "next:" + s.tool(), s.argsJson() == null ? "{}" : s.argsJson()));
        }
        Map<String, Object> actions = new LinkedHashMap<>();
        actions.put("type", "actions");
        actions.put("block_id", "next_actions");
        actions.put("elements", elements);
        return actions;
    }

    /** Convenience: returns a copy of {@code blocks} with a suggestions actions block appended. */
    public List<Map<String, Object>> withSuggestions(List<Map<String, Object>> blocks, List<Suggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) return blocks;
        List<Map<String, Object>> copy = new ArrayList<>(blocks);
        copy.add(suggestionsBlock(suggestions));
        return copy;
    }

    public record Suggestion(String label, String tool, String argsJson) {}

    private static String truncateForBlock(String s) {
        if (s == null) return "{}";
        return s.length() > 1500 ? s.substring(0, 1497) + "..." : s;
    }

    // ── primitives ───────────────────────────────────────────────────────────

    public Map<String, Object> header(String text) {
        return Map.of(
            "type", "header",
            "text", Map.of("type", "plain_text", "text", text, "emoji", true)
        );
    }

    public Map<String, Object> section(String mrkdwn) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "section");
        block.put("text", Map.of("type", "mrkdwn", "text", mrkdwn));
        return block;
    }

    public Map<String, Object> context(String mrkdwn) {
        return Map.of(
            "type", "context",
            "elements", List.of(Map.of("type", "mrkdwn", "text", mrkdwn))
        );
    }

    private static String nz(String s) { return s == null ? "" : s; }

    // ── row DTOs ─────────────────────────────────────────────────────────────

    public record AlertRow(String severity, String title, String projectName, String ageLabel) {}
    public record CapacityRow(String team, int utilisationPct, int headcount) {}
    public record CrRow(String issueKey, String summary, String stage, String priority, String assignee, String ageLabel) {}
    public record BriefingLine(String level, String text) {}
    public record ReportRow(String type, String status, String clientName, String generatedBy, String generatedAt) {}

    /** Compact CR list. */
    public List<Map<String, Object>> crs(List<CrRow> rows) {
        if (rows == null || rows.isEmpty()) return emptyState("CRs", "No matching change requests.");
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(header("Change requests (" + rows.size() + ")"));
        for (CrRow r : rows) {
            String stageEmoji = switch (r.stage() == null ? "" : r.stage().toLowerCase()) {
                case "dev", "in_dev", "development" -> ":hammer_and_wrench:";
                case "uat", "testing", "qa"         -> ":mag:";
                case "ready", "done", "released"    -> ":white_check_mark:";
                case "blocked", "on_hold"           -> ":no_entry:";
                default                              -> ":small_blue_diamond:";
            };
            String text = stageEmoji + " *<https://" + "|" + nz(r.issueKey()) + ">*  " + nz(r.summary())
                + "\n_" + nz(r.stage()) + "_"
                + (r.priority() != null ? "  ·  " + r.priority() : "")
                + (r.assignee() != null ? "  ·  " + r.assignee() : "")
                + (r.ageLabel() != null ? "  ·  " + r.ageLabel() : "");
            blocks.add(section(text));
        }
        blocks.add(context("Orbit · top " + rows.size() + " open CRs"));
        return blocks;
    }

    /** Bulleted briefing card. */
    public List<Map<String, Object>> briefing(List<BriefingLine> lines) {
        if (lines == null || lines.isEmpty()) return emptyState("Today's briefing", "All quiet — no notable signals.");
        StringBuilder sb = new StringBuilder();
        for (BriefingLine l : lines) {
            String dot = switch (l.level() == null ? "" : l.level().toLowerCase()) {
                case "critical" -> ":red_circle:";
                case "watch"    -> ":large_orange_diamond:";
                case "healthy"  -> ":large_green_circle:";
                default          -> ":small_blue_diamond:";
            };
            sb.append(dot).append(" ").append(nz(l.text())).append("\n");
        }
        return List.of(
            header("Today's briefing"),
            section(sb.toString().trim()),
            context("Orbit · " + lines.size() + " signal" + (lines.size() == 1 ? "" : "s"))
        );
    }

    /** Forecast card for a single project. */
    public List<Map<String, Object>> forecast(String projectName, Integer burnPct, String burnRate,
                                              String exhaustionDate, Integer daysToExhaust) {
        if (projectName == null) return emptyState("Forecast", "Tell me which project, e.g. `forecast Apollo`.");
        String burnDot = burnPct == null ? ":small_blue_diamond:"
                       : burnPct >= 80 ? ":red_circle:"
                       : burnPct >= 60 ? ":large_orange_diamond:"
                       : ":large_green_circle:";
        StringBuilder body = new StringBuilder()
            .append("*Project:* ").append(projectName);
        if (burnPct != null)        body.append("\n*Burned:* ").append(burnDot).append(" ").append(burnPct).append("%");
        if (burnRate != null)       body.append("\n*Burn rate:* ").append(burnRate).append(" days/day");
        if (exhaustionDate != null) body.append("\n*Forecast exhaustion:* ").append(exhaustionDate);
        if (daysToExhaust != null)  body.append("\n*Days remaining:* ~").append(daysToExhaust);
        if (burnPct == null && exhaustionDate == null) {
            body.append("\n_No man-day snapshot yet for this project._");
        }
        return List.of(
            header("Man-day forecast"),
            section(body.toString()),
            context("Orbit · derived from latest snapshot")
        );
    }

    /** Latest report runs. */
    public List<Map<String, Object>> reportStatus(List<ReportRow> rows) {
        if (rows == null || rows.isEmpty()) return emptyState("Report status", "No reports generated yet.");
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(header("Latest reports (" + rows.size() + ")"));
        for (ReportRow r : rows) {
            String emoji = switch (r.status() == null ? "" : r.status()) {
                case "COMPLETED"  -> ":white_check_mark:";
                case "GENERATING" -> ":hourglass_flowing_sand:";
                case "FAILED"     -> ":x:";
                default            -> ":grey_question:";
            };
            String text = emoji + " *" + nz(r.type()) + "*"
                + (r.clientName() != null ? "  ·  " + r.clientName() : "")
                + "\n_" + nz(r.status()) + "_"
                + (r.generatedBy() != null ? "  ·  by " + r.generatedBy() : "")
                + (r.generatedAt() != null ? "  ·  " + r.generatedAt() : "");
            blocks.add(section(text));
        }
        return blocks;
    }
}
