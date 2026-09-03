package com.orbit.service.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-only AiGateway. Returns canned responses by lookup key and records
 * every prompt for later assertion. Falls back to {@link #defaultResponse}
 * when no match is registered.
 *
 * Usage:
 *   var ai = new RecordedAiGateway()
 *       .stub("system contains:Draft executive summary", "Section text...")
 *       .stub("user contains:NX-101", "Issue NX-101 is on track");
 *   agent.run(...);
 *   assertThat(ai.calls()).hasSize(2);
 */
public class RecordedAiGateway implements AiGateway {

    public record Call(String systemPrompt, String userMessage, String model) {}

    private final List<Call> calls = new ArrayList<>();
    private final Map<String, String> stubs = new LinkedHashMap<>();
    private String defaultResponse = "stub-response";

    public RecordedAiGateway stub(String matcher, String response) {
        stubs.put(matcher, response);
        return this;
    }

    public RecordedAiGateway defaultResponse(String response) {
        this.defaultResponse = response;
        return this;
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public Call lastCall() {
        if (calls.isEmpty()) throw new IllegalStateException("no AI calls recorded");
        return calls.get(calls.size() - 1);
    }

    public void reset() {
        calls.clear();
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        return complete(systemPrompt, userMessage, null);
    }

    @Override
    public String complete(String systemPrompt, String userMessage, String model) {
        calls.add(new Call(systemPrompt, userMessage, model));
        for (var entry : stubs.entrySet()) {
            if (matches(entry.getKey(), systemPrompt, userMessage)) {
                return entry.getValue();
            }
        }
        return defaultResponse;
    }

    /**
     * Matcher syntax:
     *   "system contains:foo" — system prompt contains "foo"
     *   "user contains:foo"   — user message contains "foo"
     *   "user equals:foo"     — exact user-message match
     *   "any contains:foo"    — either side contains "foo"
     */
    private boolean matches(String matcher, String systemPrompt, String userMessage) {
        int idx = matcher.indexOf(':');
        if (idx < 0) return false;
        String head = matcher.substring(0, idx).trim();
        String needle = matcher.substring(idx + 1);
        String sys = systemPrompt == null ? "" : systemPrompt;
        String usr = userMessage == null ? "" : userMessage;
        return switch (head) {
            case "system contains" -> sys.contains(needle);
            case "user contains"   -> usr.contains(needle);
            case "user equals"     -> usr.equals(needle);
            case "any contains"    -> sys.contains(needle) || usr.contains(needle);
            default -> false;
        };
    }
}
