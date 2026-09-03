package com.orbit.service.agent;

import com.orbit.domain.client.AppUser;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Registry of native @Service agents callable by stable string key.
 *
 * Keys (lowercase, dotted) are stable contract surface for Slack slash commands
 * and the unified {@link AgentInvocationService}. Each handler returns a
 * {@code Map<String,Object>} payload that the invocation service stores as the
 * AgentRun output summary.
 */
@Component
public class NativeAgentRegistry {

    public interface NativeAgentHandler extends BiFunction<AppUser, Map<String, Object>, Map<String, Object>> {}

    private final Map<String, NativeAgentHandler> handlers = new HashMap<>();
    private final Map<String, String> descriptions = new HashMap<>();

    public NativeAgentRegistry(ReportDraftingAgent reportAgent,
                               ManDayForecastAgent forecastAgent,
                               DeliveryIntelligenceAgent briefingAgent,
                               EscalationAgent escalationAgent,
                               DevReminderAgent reminderAgent) {

        register("report.draft", "Draft contents for a previously created GeneratedReport",
            (user, args) -> {
                Long reportId = asLong(args.get("reportId"));
                String userId = user != null ? user.getEmail() : asString(args.get("userId"));
                if (reportId == null) throw new IllegalArgumentException("reportId required");
                reportAgent.draftReport(reportId, userId);
                return Map.of("reportId", reportId, "queued", true);
            });

        register("forecast.manday", "Run man-day burn forecast for all active projects",
            (user, args) -> {
                forecastAgent.runDailyForecast();
                return Map.of("started", true);
            });

        register("briefing.delivery", "Generate the daily delivery intelligence briefing",
            (user, args) -> {
                briefingAgent.runDailyBriefing();
                return Map.of("started", true);
            });

        register("escalation.draft", "Draft an escalation proposal (HITL — never auto-sends)",
            (user, args) -> {
                Long alertId = asLong(args.get("alertId"));
                if (alertId == null) throw new IllegalArgumentException("alertId required");
                escalationAgent.triggerEscalation(alertId);
                return Map.of("alertId", alertId, "proposalEmitted", true);
            });

        register("reminder.overdue", "Send overdue-item reminders to project Slack channels",
            (user, args) -> {
                reminderAgent.run();
                return Map.of("started", true);
            });
    }

    private void register(String key, String description, NativeAgentHandler handler) {
        handlers.put(key, handler);
        descriptions.put(key, description);
    }

    public boolean has(String agentKey) {
        return handlers.containsKey(agentKey);
    }

    public NativeAgentHandler get(String agentKey) {
        NativeAgentHandler h = handlers.get(agentKey);
        if (h == null) throw new IllegalArgumentException("unknown native agent key: " + agentKey);
        return h;
    }

    public Set<String> keys() {
        return Set.copyOf(handlers.keySet());
    }

    public String description(String agentKey) {
        return descriptions.getOrDefault(agentKey, "");
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
