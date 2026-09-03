package com.orbit.integration.slack;

import com.orbit.integration.slack.SlackResponseRenderer.AlertRow;
import com.orbit.integration.slack.SlackResponseRenderer.CapacityRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SlackResponseRendererTest {

    SlackResponseRenderer r = new SlackResponseRenderer();

    @SuppressWarnings("unchecked")
    private static String sectionText(Map<String, Object> block) {
        return (String) ((Map<String, Object>) block.get("text")).get("text");
    }

    @SuppressWarnings("unchecked")
    private static String headerText(Map<String, Object> block) {
        return (String) ((Map<String, Object>) block.get("text")).get("text");
    }

    @Test
    void alerts_renders_header_one_section_per_row_and_context_footer() {
        List<Map<String, Object>> blocks = r.alerts(List.of(
            new AlertRow("critical", "SLA breach NX-101", "CRM Core", "2h ago"),
            new AlertRow("warning",  "CR aging",          "Mobile",   "1d ago")
        ));
        assertThat(blocks).hasSize(4); // header + 2 sections + context
        assertThat(blocks.get(0).get("type")).isEqualTo("header");
        assertThat(headerText(blocks.get(0))).isEqualTo("Alerts (2)");

        assertThat(sectionText(blocks.get(1))).contains(":red_circle:", "SLA breach NX-101", "CRM Core", "2h ago");
        assertThat(sectionText(blocks.get(2))).contains(":large_orange_diamond:", "CR aging");

        assertThat(blocks.get(3).get("type")).isEqualTo("context");
    }

    @Test
    void alerts_empty_returns_empty_state_card() {
        List<Map<String, Object>> blocks = r.alerts(List.of());
        assertThat(blocks).hasSize(2);
        assertThat(headerText(blocks.get(0))).isEqualTo("Alerts");
        assertThat(sectionText(blocks.get(1))).contains("No matching alerts");
    }

    @Test
    void bug_summary_includes_sla_warning_when_breached_above_zero() {
        List<Map<String, Object>> blocks = r.bugSummary(1, 0, 4, 0, 2);
        assertThat(headerText(blocks.get(0))).isEqualTo("Bug summary (5)");
        assertThat(sectionText(blocks.get(1)))
            .contains("P0 *1*", "P1 *0*", "P2 *4*", "P3 *0*", ":warning: 2 SLA-breached");
    }

    @Test
    void bug_summary_omits_sla_line_when_zero_breached() {
        List<Map<String, Object>> blocks = r.bugSummary(0, 0, 3, 0, 0);
        assertThat(sectionText(blocks.get(1))).doesNotContain(":warning:");
    }

    @Test
    void bug_summary_zero_total_is_empty_state() {
        List<Map<String, Object>> blocks = r.bugSummary(0, 0, 0, 0, 0);
        assertThat(sectionText(blocks.get(1))).contains("No open bugs");
    }

    @Test
    void capacity_color_threshold_red_amber_green() {
        List<Map<String, Object>> blocks = r.capacity(List.of(
            new CapacityRow("Apollo", 92, 5),
            new CapacityRow("Borealis", 78, 6),
            new CapacityRow("Comet",  40, 4)
        ));
        assertThat(sectionText(blocks.get(1))).startsWith(":red_circle:");
        assertThat(sectionText(blocks.get(2))).startsWith(":large_orange_diamond:");
        assertThat(sectionText(blocks.get(3))).startsWith(":large_green_circle:");
    }

    @Test
    void key_value_card_renders_each_pair_as_bold_label() {
        List<Map<String, Object>> blocks = r.keyValue("Project status", java.util.Map.of(
            "Slip prob", "78%",
            "Burn",      "81%"
        ));
        assertThat(headerText(blocks.get(0))).isEqualTo("Project status");
        String body = sectionText(blocks.get(1));
        assertThat(body).contains("*Slip prob*: 78%", "*Burn*: 81%");
    }

    @Test
    void hitl_approval_card_has_approve_reject_edit_buttons_with_encoded_ids() {
        var blocks = r.hitlApprovalCard(42L, 99L, "EscalationAgent", "email.send",
            "{\"to\":\"vp@orbit.io\"}", "alerts.engine", "https://orbit.example.com");
        assertThat(blocks).hasSize(3);
        assertThat(blocks.get(0).get("type")).isEqualTo("header");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) blocks.get(1).get("text");
        String txt = (String) body.get("text");
        assertThat(txt).contains("EscalationAgent", "email.send", "alerts.engine", "vp@orbit.io");

        @SuppressWarnings("unchecked")
        var actions = (Map<String, Object>) blocks.get(2);
        assertThat(actions.get("type")).isEqualTo("actions");
        @SuppressWarnings("unchecked")
        var elements = (java.util.List<Map<String, Object>>) actions.get("elements");
        assertThat(elements).hasSize(4); // approve + reject + edit + Open-in-Orbit link
        assertThat(elements.get(0).get("action_id")).isEqualTo("hitl:approve:42:99");
        assertThat(elements.get(0).get("style")).isEqualTo("primary");
        assertThat(elements.get(1).get("action_id")).isEqualTo("hitl:reject:42:99");
        assertThat(elements.get(1).get("style")).isEqualTo("danger");
        assertThat(elements.get(2).get("action_id")).isEqualTo("hitl:edit:42:99");
        assertThat(elements.get(3).get("url")).isEqualTo("https://orbit.example.com/agents/runs/42");
    }

    @Test
    void hitl_approval_card_omits_open_in_orbit_when_base_url_blank() {
        var blocks = r.hitlApprovalCard(1L, 2L, "A", "t", "{}", "x", "");
        @SuppressWarnings("unchecked")
        var elements = (java.util.List<Map<String, Object>>) blocks.get(2).get("elements");
        assertThat(elements).hasSize(3); // no link button
    }

    @Test
    void hitl_decision_card_renders_outcome_emoji_and_note() {
        var blocks = r.hitlDecisionCard("EscalationAgent", "email.send", "REJECTED", "u@orbit.io", "wrong recipient");
        assertThat(blocks).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) blocks.get(1).get("text");
        String txt = (String) body.get("text");
        assertThat(txt).contains(":x:", "REJECTED", "u@orbit.io", "wrong recipient");
    }

    @Test
    void suggestions_block_renders_one_button_per_suggestion_with_next_action_id() {
        var block = r.suggestionsBlock(List.of(
            new SlackResponseRenderer.Suggestion("P0 bugs", "orbit.get_bugs", "{\"severity\":\"P0\"}"),
            new SlackResponseRenderer.Suggestion("Briefing", "orbit.get_briefing", "{}")
        ));
        assertThat(block.get("type")).isEqualTo("actions");
        @SuppressWarnings("unchecked")
        var elements = (List<Map<String, Object>>) block.get("elements");
        assertThat(elements).hasSize(2);
        assertThat(elements.get(0).get("action_id")).isEqualTo("next:orbit.get_bugs");
        assertThat(elements.get(0).get("value")).isEqualTo("{\"severity\":\"P0\"}");
        assertThat(elements.get(1).get("action_id")).isEqualTo("next:orbit.get_briefing");
    }

    @Test
    void with_suggestions_appends_actions_block_when_non_empty_and_noops_otherwise() {
        var base = List.<Map<String, Object>>of(r.section("hello"));
        var withSugg = r.withSuggestions(base, List.of(
            new SlackResponseRenderer.Suggestion("Briefing", "orbit.get_briefing", "{}")));
        assertThat(withSugg).hasSize(2);
        assertThat(withSugg.get(1).get("type")).isEqualTo("actions");

        assertThat(r.withSuggestions(base, List.of())).hasSize(1);
        assertThat(r.withSuggestions(base, null)).hasSize(1);
    }

    @Test
    void section_block_uses_mrkdwn_text_type() {
        Map<String, Object> s = r.section("hello *world*");
        assertThat(s.get("type")).isEqualTo("section");
        @SuppressWarnings("unchecked")
        Map<String, Object> txt = (Map<String, Object>) s.get("text");
        assertThat(txt.get("type")).isEqualTo("mrkdwn");
        assertThat(txt.get("text")).isEqualTo("hello *world*");
    }
}
