package com.orbit.integration.slack;

import com.orbit.domain.client.AppUser;
import com.orbit.service.slack.IntentResolver;
import com.orbit.service.slack.IntentResolver.ResolvedIntent;
import com.orbit.service.slack.SlackIdentityService;
import com.orbit.service.slack.SlackLinkCommandHandler;
import com.orbit.service.slack.SlackToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SlackInteractionRouterTest {

    SlackLinkCommandHandler link;
    SlackIdentityService identity;
    IntentResolver intents;
    SlackToolExecutor executor;
    SlackClient slack;
    SlackResponseRenderer renderer;
    com.orbit.service.agent.HitlApprovalService hitl;
    com.orbit.service.slack.SlackConversationStore conversations;
    SnapshotSlackHandler snapshots;
    SlackInteractionRouter router;

    @BeforeEach
    void setUp() {
        link = mock(SlackLinkCommandHandler.class);
        identity = mock(SlackIdentityService.class);
        intents = mock(IntentResolver.class);
        executor = mock(SlackToolExecutor.class);
        slack = mock(SlackClient.class);
        renderer = new SlackResponseRenderer();
        hitl = mock(com.orbit.service.agent.HitlApprovalService.class);
        conversations = mock(com.orbit.service.slack.SlackConversationStore.class);
        when(conversations.threadKey(any(), any())).thenReturn("slack_thread:t");
        when(conversations.load(any())).thenReturn(Optional.empty());
        snapshots = mock(SnapshotSlackHandler.class);
        router = new SlackInteractionRouter(link, identity, intents, executor, slack, renderer, hitl, conversations, snapshots,
            new com.orbit.config.InternalEmailDomains(""));

        // Default: a linked user, an alerts intent, a single rendered block.
        AppUser u = new AppUser();
        u.setEmail("pjm@orbit.io");
        when(identity.resolveOrbitUser("U1")).thenReturn(Optional.of(u));
        when(intents.resolve(any(), any())).thenReturn(Optional.of(
            new ResolvedIntent("orbit.get_alerts", Map.of(), "")));
        when(executor.execute(any(), any(), any())).thenReturn(List.of(Map.of("type", "section")));
        when(executor.fallbackText(any())).thenReturn("Orbit · Alerts");
    }

    @Test
    void slash_command_with_linked_user_posts_ephemeral_in_channel() {
        router.dispatchSlashCommand(Map.of(
            "command", "/orbit", "user_id", "U1", "channel_id", "C9", "text", "alerts critical"));
        verify(slack).postEphemeral(eq("C9"), eq("U1"), eq("Orbit · Alerts"), any());
        verifyNoInteractions(link);
    }

    @Test
    void slash_command_routes_orbit_link_to_handler() {
        router.dispatchSlashCommand(Map.of(
            "command", "/orbit-link", "user_id", "U1", "text", "pjm@orbit.io"));
        verify(link).handle("U1", "pjm@orbit.io");
        verifyNoInteractions(slack, identity, intents, executor);
    }

    @Test
    void unlinked_user_gets_link_prompt_ephemeral_not_real_response() {
        when(identity.resolveOrbitUser("U1")).thenReturn(Optional.empty());
        router.dispatchSlashCommand(Map.of(
            "command", "/orbit", "user_id", "U1", "channel_id", "C9", "text", "alerts"));
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(slack).postEphemeral(eq("C9"), eq("U1"), msg.capture(), any());
        assertThat(msg.getValue()).contains("/orbit-link");
        verifyNoInteractions(intents, executor);
    }

    @Test
    void app_mention_event_posts_in_thread() throws Exception {
        String body = """
            {"type":"event_callback","event":{
              "type":"app_mention",
              "user":"U1","channel":"C9",
              "ts":"1700.001",
              "text":"<@UBOT> alerts critical"
            }}
            """;
        router.dispatchEvent(body);
        verify(slack).postInThread(eq("C9"), eq("1700.001"), eq("Orbit · Alerts"), any());
    }

    @Test
    void app_mention_uses_thread_ts_when_already_threaded() throws Exception {
        String body = """
            {"type":"event_callback","event":{
              "type":"app_mention",
              "user":"U1","channel":"C9",
              "ts":"1700.005","thread_ts":"1700.001",
              "text":"<@UBOT> bugs"
            }}
            """;
        router.dispatchEvent(body);
        verify(slack).postInThread(eq("C9"), eq("1700.001"), any(), any());
    }

    @Test
    void direct_message_event_posts_in_dm_channel() throws Exception {
        String body = """
            {"type":"event_callback","event":{
              "type":"message","channel_type":"im",
              "user":"U1","channel":"D9","text":"alerts"
            }}
            """;
        router.dispatchEvent(body);
        verify(slack).postMessage(eq("D9"), eq("Orbit · Alerts"), any());
    }

    @Test
    void run_intent_in_thread_posts_placeholder_then_updates_with_result() throws Exception {
        when(intents.resolve(any(), any())).thenReturn(Optional.of(
            new com.orbit.service.slack.IntentResolver.ResolvedIntent("orbit.run_forecast", java.util.Map.of(), "")));
        when(executor.execute(any(), any(), any())).thenReturn(List.of(Map.of("type", "section", "done", true)));
        when(executor.fallbackText(any())).thenReturn("Orbit · Forecast run");
        when(slack.postInThread(eq("C9"), eq("1700.001"), any(), any()))
            .thenReturn(Map.of("ok", true, "ts", "1700.999", "channel", "C9"));

        router.dispatchEvent("""
            {"type":"event_callback","event":{
              "type":"app_mention","user":"U1","channel":"C9","ts":"1700.001",
              "text":"<@UBOT> run forecast"
            }}
            """);

        ArgumentCaptor<String> phText = ArgumentCaptor.forClass(String.class);
        verify(slack).postInThread(eq("C9"), eq("1700.001"), phText.capture(), any());
        assertThat(phText.getValue()).contains("working");
        verify(slack).updateMessage(eq("C9"), eq("1700.999"), eq("Orbit · Forecast run"), any());
    }

    @Test
    void run_intent_in_dm_posts_placeholder_then_updates_via_chat_update() throws Exception {
        when(intents.resolve(any(), any())).thenReturn(Optional.of(
            new com.orbit.service.slack.IntentResolver.ResolvedIntent("orbit.run_briefing", java.util.Map.of(), "")));
        when(executor.execute(any(), any(), any())).thenReturn(List.of(Map.of("type", "section")));
        when(executor.fallbackText(any())).thenReturn("Orbit · Delivery briefing run");
        when(slack.postMessage(eq("D9"), any(), any()))
            .thenReturn(Map.of("ok", true, "ts", "1700.500", "channel", "D9"));

        router.dispatchEvent("""
            {"type":"event_callback","event":{
              "type":"message","channel_type":"im","user":"U1","channel":"D9",
              "text":"kick off briefing"
            }}
            """);

        verify(slack).postMessage(eq("D9"), any(), any());
        verify(slack).updateMessage(eq("D9"), eq("1700.500"), any(), any());
    }

    @Test
    void run_intent_via_slash_does_not_use_placeholder_pattern() {
        when(intents.resolve(any(), any())).thenReturn(Optional.of(
            new com.orbit.service.slack.IntentResolver.ResolvedIntent("orbit.run_forecast", java.util.Map.of(), "")));
        router.dispatchSlashCommand(Map.of(
            "command", "/orbit", "user_id", "U1", "channel_id", "C9", "text", "run forecast"));
        // Ephemeral can't be chat.updated — single ephemeral post, no updateMessage.
        verify(slack).postEphemeral(eq("C9"), eq("U1"), any(), any());
        verify(slack, never()).updateMessage(any(), any(), any(), any());
    }

    @Test
    void block_action_approve_calls_hitl_approve_and_replaces_original_via_response_url() throws Exception {
        AppUser u = new AppUser(); u.setEmail("pjm@orbit.io");
        when(identity.resolveOrbitUser("U1")).thenReturn(Optional.of(u));

        String payload = """
            {"type":"block_actions","user":{"id":"U1"},
             "response_url":"https://hooks.slack.com/r/abc",
             "trigger_id":"T1",
             "actions":[{"action_id":"hitl:approve:42:99","value":"99"}]}
            """;
        router.dispatchInteraction(payload);

        verify(hitl).approve(eq(42L), eq(99L), eq(null), eq("pjm@orbit.io"));
        verify(slack).respondViaUrl(eq("https://hooks.slack.com/r/abc"), any());
    }

    @Test
    void block_action_reject_opens_modal_via_views_open() throws Exception {
        AppUser u = new AppUser(); u.setEmail("pjm@orbit.io");
        when(identity.resolveOrbitUser("U1")).thenReturn(Optional.of(u));

        String payload = """
            {"type":"block_actions","user":{"id":"U1"},
             "response_url":"https://hooks.slack.com/r/abc",
             "trigger_id":"T1",
             "actions":[{"action_id":"hitl:reject:7:8"}]}
            """;
        router.dispatchInteraction(payload);

        ArgumentCaptor<java.util.Map<String,Object>> view = ArgumentCaptor.forClass(java.util.Map.class);
        verify(slack).openView(eq("T1"), view.capture());
        assertThat(view.getValue().get("callback_id")).isEqualTo("hitl_reject");
        assertThat(view.getValue().get("private_metadata").toString()).contains("runId=7", "stepId=8");
        verifyNoInteractions(hitl);
    }

    @Test
    void block_action_edit_opens_modal_prefilled_with_args_from_message() throws Exception {
        AppUser u = new AppUser(); u.setEmail("pjm@orbit.io");
        when(identity.resolveOrbitUser("U1")).thenReturn(Optional.of(u));

        String payload = """
            {"type":"block_actions","user":{"id":"U1"},
             "response_url":"https://hooks.slack.com/r/abc",
             "trigger_id":"T1",
             "message":{"blocks":[
               {"type":"header"},
               {"type":"section","text":{"type":"mrkdwn","text":"args:\\n```{\\"to\\":\\"vp@orbit.io\\"}```"}}
             ]},
             "actions":[{"action_id":"hitl:edit:5:6"}]}
            """;
        router.dispatchInteraction(payload);

        ArgumentCaptor<java.util.Map<String,Object>> view = ArgumentCaptor.forClass(java.util.Map.class);
        verify(slack).openView(eq("T1"), view.capture());
        assertThat(view.getValue().get("callback_id")).isEqualTo("hitl_edit");
    }

    @Test
    void block_action_from_unlinked_user_returns_link_hint_via_response_url() throws Exception {
        when(identity.resolveOrbitUser("U1")).thenReturn(Optional.empty());

        String payload = """
            {"type":"block_actions","user":{"id":"U1"},
             "response_url":"https://hooks.slack.com/r/abc",
             "actions":[{"action_id":"hitl:approve:1:1"}]}
            """;
        router.dispatchInteraction(payload);

        ArgumentCaptor<java.util.Map<String,Object>> body = ArgumentCaptor.forClass(java.util.Map.class);
        verify(slack).respondViaUrl(eq("https://hooks.slack.com/r/abc"), body.capture());
        assertThat(body.getValue().get("text").toString()).contains("/orbit-link");
        verifyNoInteractions(hitl);
    }

    @Test
    void view_submission_reject_calls_hitl_reject_with_reason() throws Exception {
        AppUser u = new AppUser(); u.setEmail("pjm@orbit.io");
        when(identity.resolveOrbitUser("U1")).thenReturn(Optional.of(u));

        String payload = """
            {"type":"view_submission","user":{"id":"U1"},
             "view":{
               "callback_id":"hitl_reject",
               "private_metadata":"runId=42;stepId=99;responseUrl=https://hooks.slack.com/r/abc",
               "state":{"values":{"reject_reason_block":{"reason":{"value":"wrong recipient"}}}}
             }}
            """;
        router.dispatchInteraction(payload);

        verify(hitl).reject(eq(42L), eq(99L), eq("wrong recipient"), eq("pjm@orbit.io"));
        verify(slack).respondViaUrl(eq("https://hooks.slack.com/r/abc"), any());
    }

    @Test
    void view_submission_edit_calls_hitl_approve_with_parsed_args() throws Exception {
        AppUser u = new AppUser(); u.setEmail("pjm@orbit.io");
        when(identity.resolveOrbitUser("U1")).thenReturn(Optional.of(u));

        String payload = """
            {"type":"view_submission","user":{"id":"U1"},
             "view":{
               "callback_id":"hitl_edit",
               "private_metadata":"runId=42;stepId=99;responseUrl=https://hooks.slack.com/r/abc",
               "state":{"values":{"edit_args_block":{"args":{"value":"{\\"to\\":\\"new@orbit.io\\"}"}}}}
             }}
            """;
        router.dispatchInteraction(payload);

        ArgumentCaptor<java.util.Map<String,Object>> args = ArgumentCaptor.forClass(java.util.Map.class);
        verify(hitl).approve(eq(42L), eq(99L), args.capture(), eq("pjm@orbit.io"));
        assertThat(args.getValue()).containsEntry("to", "new@orbit.io");
    }

    // ── Phase 4: conversation context + next-action buttons ─────────────────

    @Test
    void respond_loads_thread_context_and_passes_inherited_args_to_intent_resolver() throws Exception {
        when(conversations.load("slack_thread:1700.001")).thenReturn(Optional.of(
            new com.orbit.service.slack.SlackConversationStore.SlackTurnContext(
                "orbit.get_bugs", "Apollo", null, null)));
        when(conversations.threadKey("C9", "1700.001")).thenReturn("slack_thread:1700.001");

        router.dispatchEvent("""
            {"type":"event_callback","event":{
              "type":"app_mention","user":"U1","channel":"C9","ts":"1700.001",
              "text":"<@UBOT> alerts"
            }}
            """);

        ArgumentCaptor<Map<String, Object>> prior = ArgumentCaptor.forClass(Map.class);
        verify(intents).resolve(eq("alerts"), prior.capture());
        assertThat(prior.getValue()).containsEntry("projectName", "Apollo");
    }

    @Test
    void respond_saves_turn_context_after_successful_intent_resolution() throws Exception {
        when(intents.resolve(any(), any())).thenReturn(Optional.of(
            new ResolvedIntent("orbit.get_bugs", Map.of("severity", "P0", "projectName", "Apollo"), "")));
        when(conversations.threadKey(any(), any())).thenReturn("slack_thread:k");

        router.dispatchSlashCommand(Map.of(
            "command", "/orbit", "user_id", "U1", "channel_id", "C9", "text", "bugs p0 apollo"));

        ArgumentCaptor<com.orbit.service.slack.SlackConversationStore.SlackTurnContext> ctx =
            ArgumentCaptor.forClass(com.orbit.service.slack.SlackConversationStore.SlackTurnContext.class);
        verify(conversations).save(eq("slack_thread:k"), ctx.capture());
        assertThat(ctx.getValue().lastTool()).isEqualTo("orbit.get_bugs");
        assertThat(ctx.getValue().projectName()).isEqualTo("Apollo");
        assertThat(ctx.getValue().severity()).isEqualTo("P0");
    }

    @Test
    void respond_appends_suggested_next_actions_to_result_card() throws Exception {
        when(executor.suggestionsFor(any())).thenReturn(List.of(
            new SlackResponseRenderer.Suggestion("Briefing", "orbit.get_briefing", "{}")));

        router.dispatchSlashCommand(Map.of(
            "command", "/orbit", "user_id", "U1", "channel_id", "C9", "text", "alerts"));

        ArgumentCaptor<java.util.List<Map<String, Object>>> blocksCap = ArgumentCaptor.forClass(java.util.List.class);
        verify(slack).postEphemeral(eq("C9"), eq("U1"), any(), blocksCap.capture());
        var blocks = blocksCap.getValue();
        // Default executor stub returns one section; suggestions block is appended.
        assertThat(blocks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(blocks.get(blocks.size() - 1).get("type")).isEqualTo("actions");
    }

    @Test
    void next_action_button_dispatches_a_new_intent_and_posts_in_thread() throws Exception {
        AppUser u = new AppUser(); u.setEmail("pjm@orbit.io");
        when(identity.resolveOrbitUser("U1")).thenReturn(Optional.of(u));
        when(executor.execute(any(), any(), any())).thenReturn(List.of(Map.of("type", "section")));
        when(executor.fallbackText(any())).thenReturn("Orbit · Bugs");
        when(conversations.threadKey("C9", "1700.001")).thenReturn("slack_thread:1700.001");
        when(conversations.load("slack_thread:1700.001")).thenReturn(Optional.of(
            new com.orbit.service.slack.SlackConversationStore.SlackTurnContext(
                "orbit.get_alerts", "Apollo", null, null)));

        String payload = """
            {"type":"block_actions","user":{"id":"U1"},
             "response_url":"https://hooks.slack.com/r/abc",
             "channel":{"id":"C9"},
             "message":{"ts":"1700.001"},
             "actions":[{"action_id":"next:orbit.get_bugs","value":"{\\"severity\\":\\"P0\\"}"}]}
            """;
        router.dispatchInteraction(payload);

        ArgumentCaptor<ResolvedIntent> intentCap = ArgumentCaptor.forClass(ResolvedIntent.class);
        verify(executor).execute(intentCap.capture(), eq(u), any());
        assertThat(intentCap.getValue().tool()).isEqualTo("orbit.get_bugs");
        assertThat(intentCap.getValue().args())
            .containsEntry("severity", "P0")
            .containsEntry("projectName", "Apollo");
        verify(slack).postInThread(eq("C9"), eq("1700.001"), eq("Orbit · Bugs"), any());
    }

    @Test
    void next_action_button_from_unlinked_user_returns_link_hint() throws Exception {
        when(identity.resolveOrbitUser("U1")).thenReturn(Optional.empty());

        String payload = """
            {"type":"block_actions","user":{"id":"U1"},
             "response_url":"https://hooks.slack.com/r/abc",
             "actions":[{"action_id":"next:orbit.get_bugs","value":"{}"}]}
            """;
        router.dispatchInteraction(payload);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(slack).respondViaUrl(eq("https://hooks.slack.com/r/abc"), body.capture());
        assertThat(body.getValue().get("text").toString()).contains("/orbit-link");
        verifyNoInteractions(executor);
    }

    @Test
    void mention_strips_bot_handle_before_intent_resolution() throws Exception {
        String body = """
            {"type":"event_callback","event":{
              "type":"app_mention","user":"U1","channel":"C9","ts":"1700.001",
              "text":"<@UBOT> any open p0 bugs?"
            }}
            """;
        router.dispatchEvent(body);
        ArgumentCaptor<String> txt = ArgumentCaptor.forClass(String.class);
        verify(intents).resolve(txt.capture(), any());
        assertThat(txt.getValue()).isEqualTo("any open p0 bugs?");
    }
}
