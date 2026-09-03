package com.orbit.service.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.service.ai.AiGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a free-text Slack query into a {@link ResolvedIntent} naming one of the
 * read-only orbit.get_* tools and its arguments.
 *
 * Strategy: try the deterministic slash-keyword parser first (zero-cost, predictable),
 * fall back to the Haiku-tier AiGateway in JSON-mode. Either may return empty,
 * which the caller renders as "I didn't understand that".
 *
 * Phase 1: read-only allowlist. Write tools (escalation send, report generate) come
 * in Phase 2 with explicit invocation paths.
 */
@Service
public class IntentResolver {

    private static final Logger log = LoggerFactory.getLogger(IntentResolver.class);
    private static final String MODEL = "claude-haiku-4-5-20251001";

    /** Read-only tools. */
    public static final List<String> READ_ONLY_TOOLS = List.of(
        "orbit.get_alerts",
        "orbit.get_bugs",
        "orbit.get_crs",
        "orbit.get_briefing",
        "orbit.get_forecast",
        "orbit.get_capacity",
        "orbit.get_report_status"
    );

    /** Phase 2 invocation tools — kick off an agent run. */
    public static final List<String> INVOCATION_TOOLS = List.of(
        "orbit.run_report",
        "orbit.run_forecast",
        "orbit.run_briefing"
    );

    /** Allowlist enforced on the LLM output. */
    private static final java.util.Set<String> ALL_TOOLS = new java.util.HashSet<>() {{
        addAll(READ_ONLY_TOOLS);
        addAll(INVOCATION_TOOLS);
    }};

    private static final String SYSTEM_PROMPT = """
        You are Orbit's Slack intent router. Translate the user's message into a single tool call.
        Return ONLY a JSON object with this shape, no prose, no markdown:
          {"tool": "<tool_name>", "args": {<key>: <value>, ...}, "reasoning": "<one short sentence>"}
        Allowed tool names (use exactly one):
          orbit.get_alerts        args: {severity?: "critical"|"warning"|"info", projectName?: string}
          orbit.get_bugs          args: {severity?: "P0"|"P1"|"P2"|"P3", projectName?: string}
          orbit.get_crs           args: {stage?: string, projectName?: string}
          orbit.get_briefing      args: {} (daily delivery briefing)
          orbit.get_forecast      args: {projectName?: string}
          orbit.get_capacity      args: {team?: string}
          orbit.get_report_status args: {reportId?: number, type?: string}
          orbit.run_report        args: {type?: "weekly"|"adhoc", projectName?: string}  (kicks off ReportDraftingAgent)
          orbit.run_forecast      args: {projectName?: string}                           (kicks off ManDayForecastAgent)
          orbit.run_briefing      args: {}                                               (kicks off DeliveryIntelligenceAgent)
        Choose run_* only when the user clearly asks to GENERATE, RUN, KICK OFF, or PRODUCE something.
        Prefer the corresponding get_* tool when they're asking about existing data.
        If the user's intent does not match any tool, return {"tool": null, "args": {}, "reasoning": "..."}.
        Never invent tool names. Never include any text outside the JSON object.
        """;

    private final AiGateway ai;
    private final ObjectMapper mapper = new ObjectMapper();

    public IntentResolver(AiGateway ai) {
        this.ai = ai;
    }

    public Optional<ResolvedIntent> resolve(String userText) {
        return resolve(userText, null);
    }

    /**
     * Resolve with prior-turn context. If the new message is a short follow-up
     * (no own project/severity), inheritable values from {@code priorArgs} are folded
     * into the returned intent's args.
     */
    public Optional<ResolvedIntent> resolve(String userText, Map<String, Object> priorArgs) {
        if (userText == null || userText.isBlank()) return Optional.empty();
        Optional<ResolvedIntent> deterministic = parseSlash(userText.trim());
        Optional<ResolvedIntent> resolved = deterministic.isPresent() ? deterministic : resolveViaLlm(userText.trim());
        if (priorArgs == null || priorArgs.isEmpty()) return resolved;
        return resolved.map(r -> mergePrior(r, priorArgs));
    }

    private static ResolvedIntent mergePrior(ResolvedIntent r, Map<String, Object> priorArgs) {
        Map<String, Object> merged = new HashMap<>(priorArgs);
        merged.putAll(r.args()); // current turn wins
        return new ResolvedIntent(r.tool(), merged, r.reasoning());
    }

    // ── deterministic parser ─────────────────────────────────────────────────

    private Optional<ResolvedIntent> parseSlash(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String[] parts = lower.split("\\s+");
        if (parts.length == 0) return Optional.empty();
        String head = parts[0];
        Map<String, Object> args = new HashMap<>();
        if ((head.equals("run") || head.equals("generate") || head.equals("kick")) && parts.length > 1) {
            int targetIdx = head.equals("kick") && parts.length > 2 && parts[1].equals("off") ? 2 : 1;
            if (parts.length > targetIdx) {
                String target = parts[targetIdx];
                switch (target) {
                    case "report":
                        return Optional.of(new ResolvedIntent("orbit.run_report", args, "deterministic: '" + head + " report'"));
                    case "forecast": {
                        if (parts.length > targetIdx + 1) {
                            int spaceIdx = nthSpaceIndex(text, targetIdx + 1);
                            if (spaceIdx > 0) {
                                args.put("projectName", capitalise(text.substring(spaceIdx + 1).trim()));
                            }
                        }
                        return Optional.of(new ResolvedIntent("orbit.run_forecast", args, "deterministic: '" + head + " forecast'"));
                    }
                    case "briefing":
                        return Optional.of(new ResolvedIntent("orbit.run_briefing", args, "deterministic: '" + head + " briefing'"));
                }
            }
        }
        switch (head) {
            case "alerts": {
                if (parts.length > 1) {
                    String sev = parts[1];
                    if (sev.equals("critical") || sev.equals("warning") || sev.equals("info")) {
                        args.put("severity", sev);
                    }
                }
                return Optional.of(new ResolvedIntent("orbit.get_alerts", args, "deterministic: 'alerts' keyword"));
            }
            case "bugs": {
                if (parts.length > 1 && parts[1].matches("p[0-3]")) {
                    args.put("severity", parts[1].toUpperCase(Locale.ROOT));
                }
                return Optional.of(new ResolvedIntent("orbit.get_bugs", args, "deterministic: 'bugs' keyword"));
            }
            case "crs":
            case "cr":
                return Optional.of(new ResolvedIntent("orbit.get_crs", args, "deterministic: 'crs' keyword"));
            case "briefing":
                return Optional.of(new ResolvedIntent("orbit.get_briefing", args, "deterministic: 'briefing' keyword"));
            case "forecast": {
                if (parts.length > 1) {
                    args.put("projectName", capitalise(text.substring(text.indexOf(' ') + 1).trim()));
                }
                return Optional.of(new ResolvedIntent("orbit.get_forecast", args, "deterministic: 'forecast' keyword"));
            }
            case "capacity":
                return Optional.of(new ResolvedIntent("orbit.get_capacity", args, "deterministic: 'capacity' keyword"));
            case "report":
                return Optional.of(new ResolvedIntent("orbit.get_report_status", args, "deterministic: 'report' keyword"));
            default:
                return Optional.empty();
        }
    }

    private static int nthSpaceIndex(String s, int n) {
        int idx = -1;
        for (int i = 0; i < n; i++) {
            idx = s.indexOf(' ', idx + 1);
            if (idx < 0) return -1;
        }
        return idx;
    }

    private static String capitalise(String s) {
        if (s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── LLM fallback ─────────────────────────────────────────────────────────

    private Optional<ResolvedIntent> resolveViaLlm(String userText) {
        String raw;
        try {
            raw = ai.complete(SYSTEM_PROMPT, userText, MODEL);
        } catch (RuntimeException e) {
            log.warn("IntentResolver LLM call failed: {}", e.getMessage());
            return Optional.empty();
        }
        if (raw == null || raw.isBlank()) return Optional.empty();
        String trimmed = stripCodeFence(raw.trim());
        try {
            JsonNode root = mapper.readTree(trimmed);
            JsonNode toolNode = root.get("tool");
            if (toolNode == null || toolNode.isNull()) return Optional.empty();
            String tool = toolNode.asText();
            if (!ALL_TOOLS.contains(tool)) {
                log.warn("IntentResolver dropped tool not in allowlist: {}", tool);
                return Optional.empty();
            }
            Map<String, Object> args = new HashMap<>();
            JsonNode argsNode = root.get("args");
            if (argsNode != null && argsNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = argsNode.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    JsonNode v = e.getValue();
                    if (v.isTextual()) args.put(e.getKey(), v.asText());
                    else if (v.isNumber()) args.put(e.getKey(), v.numberValue());
                    else if (v.isBoolean()) args.put(e.getKey(), v.asBoolean());
                }
            }
            String reasoning = root.path("reasoning").asText("");
            return Optional.of(new ResolvedIntent(tool, args, reasoning));
        } catch (Exception e) {
            log.warn("IntentResolver could not parse LLM JSON response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String stripCodeFence(String s) {
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (firstNl > 0 && end > firstNl) return s.substring(firstNl + 1, end).trim();
        }
        return s;
    }

    public record ResolvedIntent(String tool, Map<String, Object> args, String reasoning) {}
}
