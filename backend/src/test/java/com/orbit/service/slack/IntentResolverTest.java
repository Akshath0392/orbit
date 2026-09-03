package com.orbit.service.slack;

import com.orbit.service.ai.RecordedAiGateway;
import com.orbit.service.slack.IntentResolver.ResolvedIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IntentResolverTest {

    RecordedAiGateway ai;
    IntentResolver resolver;

    @BeforeEach
    void setUp() {
        ai = new RecordedAiGateway();
        resolver = new IntentResolver(ai);
    }

    // ── deterministic parser ─────────────────────────────────────────────────

    @Test
    void deterministic_alerts_critical_returns_severity_arg() {
        Optional<ResolvedIntent> out = resolver.resolve("alerts critical");
        assertThat(out).isPresent();
        assertThat(out.get().tool()).isEqualTo("orbit.get_alerts");
        assertThat(out.get().args()).containsEntry("severity", "critical");
        assertThat(ai.calls()).isEmpty();
    }

    @Test
    void deterministic_bugs_p0_uppercases_severity() {
        Optional<ResolvedIntent> out = resolver.resolve("bugs p0");
        assertThat(out).isPresent();
        assertThat(out.get().tool()).isEqualTo("orbit.get_bugs");
        assertThat(out.get().args()).containsEntry("severity", "P0");
    }

    @Test
    void deterministic_briefing_no_args() {
        Optional<ResolvedIntent> out = resolver.resolve("briefing");
        assertThat(out).isPresent();
        assertThat(out.get().tool()).isEqualTo("orbit.get_briefing");
        assertThat(out.get().args()).isEmpty();
    }

    @Test
    void deterministic_forecast_apollo_captures_project_name() {
        Optional<ResolvedIntent> out = resolver.resolve("forecast apollo");
        assertThat(out).isPresent();
        assertThat(out.get().tool()).isEqualTo("orbit.get_forecast");
        assertThat(out.get().args()).containsEntry("projectName", "Apollo");
    }

    @Test
    void blank_input_returns_empty() {
        assertThat(resolver.resolve(null)).isEmpty();
        assertThat(resolver.resolve("")).isEmpty();
        assertThat(resolver.resolve("   ")).isEmpty();
    }

    @Test
    void deterministic_run_report_routes_to_invocation_tool() {
        Optional<ResolvedIntent> out = resolver.resolve("run report");
        assertThat(out).isPresent();
        assertThat(out.get().tool()).isEqualTo("orbit.run_report");
        assertThat(ai.calls()).isEmpty();
    }

    @Test
    void deterministic_generate_report_routes_to_invocation_tool() {
        Optional<ResolvedIntent> out = resolver.resolve("generate report");
        assertThat(out).isPresent();
        assertThat(out.get().tool()).isEqualTo("orbit.run_report");
    }

    @Test
    void deterministic_kick_off_briefing_routes_to_run_briefing() {
        Optional<ResolvedIntent> out = resolver.resolve("kick off briefing");
        assertThat(out).isPresent();
        assertThat(out.get().tool()).isEqualTo("orbit.run_briefing");
    }

    @Test
    void deterministic_run_forecast_captures_project_name() {
        Optional<ResolvedIntent> out = resolver.resolve("run forecast borealis");
        assertThat(out).isPresent();
        assertThat(out.get().tool()).isEqualTo("orbit.run_forecast");
        assertThat(out.get().args()).containsEntry("projectName", "Borealis");
    }

    @Test
    void llm_allowlist_now_accepts_run_tools() {
        ai.defaultResponse("""
            {"tool":"orbit.run_report","args":{"type":"weekly"},"reasoning":"explicit generate"}
            """);
        Optional<ResolvedIntent> out = resolver.resolve("please generate the weekly report");
        assertThat(out).isPresent();
        assertThat(out.get().tool()).isEqualTo("orbit.run_report");
        assertThat(out.get().args()).containsEntry("type", "weekly");
    }

    // ── LLM fallback ─────────────────────────────────────────────────────────

    @Test
    void llm_routes_natural_language_to_get_alerts() {
        ai.defaultResponse("""
            {"tool":"orbit.get_alerts","args":{"severity":"critical"},"reasoning":"asks for urgent items"}
            """);
        Optional<ResolvedIntent> out = resolver.resolve("anything urgent I should look at?");
        assertThat(out).isPresent();
        assertThat(out.get().tool()).isEqualTo("orbit.get_alerts");
        assertThat(out.get().args()).containsEntry("severity", "critical");
        assertThat(ai.calls()).hasSize(1);
        // Haiku model passed through
        assertThat(ai.lastCall().model()).isEqualTo("claude-haiku-4-5-20251001");
    }

    @Test
    void llm_strips_markdown_code_fence() {
        ai.defaultResponse("""
            ```json
            {"tool":"orbit.get_briefing","args":{},"reasoning":"daily summary"}
            ```
            """);
        Optional<ResolvedIntent> out = resolver.resolve("what's happening today");
        assertThat(out).isPresent();
        assertThat(out.get().tool()).isEqualTo("orbit.get_briefing");
    }

    @Test
    void llm_returning_tool_outside_allowlist_is_dropped() {
        ai.defaultResponse("""
            {"tool":"orbit.send_escalation","args":{},"reasoning":"escalate"}
            """);
        assertThat(resolver.resolve("send a slack message to the team")).isEmpty();
    }

    @Test
    void llm_returning_null_tool_is_dropped() {
        ai.defaultResponse("""
            {"tool":null,"args":{},"reasoning":"unsupported"}
            """);
        assertThat(resolver.resolve("what's the weather")).isEmpty();
    }

    @Test
    void llm_returning_invalid_json_is_dropped() {
        ai.defaultResponse("Sorry, I cannot help with that.");
        assertThat(resolver.resolve("vague unstructured ask")).isEmpty();
    }

    @Test
    void llm_call_failure_returns_empty_not_throws() {
        IntentResolver r = new IntentResolver(new com.orbit.service.ai.AiGateway() {
            @Override public String complete(String s, String u) { throw new RuntimeException("api down"); }
        });
        assertThat(r.resolve("any updates")).isEmpty();
    }

    // ── Phase 4: context-aware follow-ups ────────────────────────────────────

    @Test
    void prior_project_is_inherited_when_followup_has_no_project() {
        Optional<ResolvedIntent> out = resolver.resolve("bugs",
            java.util.Map.of("projectName", "Apollo"));
        assertThat(out).isPresent();
        assertThat(out.get().tool()).isEqualTo("orbit.get_bugs");
        assertThat(out.get().args()).containsEntry("projectName", "Apollo");
    }

    @Test
    void current_turn_project_overrides_prior_when_both_present() {
        Optional<ResolvedIntent> out = resolver.resolve("forecast Borealis",
            java.util.Map.of("projectName", "Apollo"));
        assertThat(out).isPresent();
        assertThat(out.get().args()).containsEntry("projectName", "Borealis");
    }

    @Test
    void prior_severity_inherits_into_alerts_followup() {
        Optional<ResolvedIntent> out = resolver.resolve("alerts",
            java.util.Map.of("severity", "critical"));
        assertThat(out).isPresent();
        assertThat(out.get().args()).containsEntry("severity", "critical");
    }
}
