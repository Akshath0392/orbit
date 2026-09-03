package com.orbit.integration.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.config.InternalEmailDomains;
import com.orbit.domain.client.AppUser;
import com.orbit.service.agent.HitlApprovalService;
import com.orbit.service.slack.IntentResolver;
import com.orbit.service.slack.IntentResolver.ResolvedIntent;
import com.orbit.service.slack.SlackConversationStore;
import com.orbit.service.slack.SlackConversationStore.SlackTurnContext;
import com.orbit.service.slack.SlackIdentityService;
import com.orbit.service.slack.SlackLinkCommandHandler;
import com.orbit.service.slack.SlackToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * End-to-end Slack inbound pipeline:
 *   identity → intent → tool exec → render → post.
 * Channel queries reply ephemeral; mentions reply in-thread; DMs reply in DM.
 */
@Component
public class SlackInteractionRouter {

    private static final Logger log = LoggerFactory.getLogger(SlackInteractionRouter.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final SlackLinkCommandHandler linkHandler;
    private final SlackIdentityService identity;
    private final IntentResolver intents;
    private final SlackToolExecutor executor;
    private final SlackClient slack;
    private final SlackResponseRenderer renderer;
    private final HitlApprovalService hitl;
    private final SlackConversationStore conversations;
    private final SnapshotSlackHandler snapshots;
    private final InternalEmailDomains internalDomains;

    public SlackInteractionRouter(SlackLinkCommandHandler linkHandler,
                                  SlackIdentityService identity,
                                  IntentResolver intents,
                                  SlackToolExecutor executor,
                                  SlackClient slack,
                                  SlackResponseRenderer renderer,
                                  HitlApprovalService hitl,
                                  SlackConversationStore conversations,
                                  SnapshotSlackHandler snapshots,
                                  InternalEmailDomains internalDomains) {
        this.linkHandler = linkHandler;
        this.identity = identity;
        this.intents = intents;
        this.executor = executor;
        this.slack = slack;
        this.renderer = renderer;
        this.hitl = hitl;
        this.conversations = conversations;
        this.snapshots = snapshots;
        this.internalDomains = internalDomains;
    }

    @Async
    public void dispatchEvent(String rawBody) {
        try {
            JsonNode root = mapper.readTree(rawBody);
            JsonNode ev = root.path("event");
            String evType = text(ev, "type");
            String slackUser = text(ev, "user");
            String channel = text(ev, "channel");
            String text = text(ev, "text");
            String threadTs = text(ev, "thread_ts");
            if (threadTs == null) threadTs = text(ev, "ts");
            log.info("Slack event: type={} user={} channel={} threadTs={}", evType, slackUser, channel, threadTs);

            // Ignore the bot's own messages to avoid self-reply loops.
            String botId = text(ev, "bot_id");
            String subtype = text(ev, "subtype");
            if (botId != null || "bot_message".equals(subtype) || slackUser == null) {
                return;
            }

            if ("app_mention".equals(evType)) {
                respond(slackUser, channel, threadTs, stripMention(text), Surface.MENTION);
            } else if ("message".equals(evType) && "im".equals(text(ev, "channel_type"))) {
                respond(slackUser, channel, null, text, Surface.DM);
            }
        } catch (Exception e) {
            log.warn("Slack event parse failed: {}", e.getMessage());
        }
    }

    @Async
    public void dispatchSlashCommand(Map<String, String> form) {
        String cmd = form.getOrDefault("command", "");
        String slackUser = form.get("user_id");
        String channel = form.get("channel_id");
        String text = form.get("text");
        log.info("Slack slash: command={} user={} channel={}", cmd, slackUser, channel);
        if ("/orbit-link".equals(cmd)) { linkHandler.handle(slackUser, text); return; }
        if ("/orbit".equals(cmd)) {
            if (snapshots.matchesSlashText(text)) {
                Optional<AppUser> who = identity.resolveOrbitUser(slackUser);
                if (who.isEmpty()) {
                    sendLinkPrompt(slackUser, channel, null, Surface.SLASH);
                    return;
                }
                if (!snapshots.openModal(form.get("trigger_id"), who.get())) {
                    slack.postEphemeral(channel, slackUser,
                        "Could not open snapshot modal — please try again.", List.of());
                }
                return;
            }
            respond(slackUser, channel, null, text, Surface.SLASH);
            return;
        }
        log.warn("Unknown Slack slash command: {}", cmd);
    }

    @Async
    public void dispatchInteraction(String payloadJson) {
        try {
            JsonNode root = mapper.readTree(payloadJson);
            String type = text(root, "type");
            String slackUserId = text(root.path("user"), "id");
            log.info("Slack interaction: type={} user={}", type, slackUserId);
            if ("block_actions".equals(type))   handleBlockAction(root, slackUserId);
            else if ("view_submission".equals(type)) handleViewSubmission(root, slackUserId);
        } catch (Exception e) {
            log.warn("Slack interaction parse failed: {}", e.getMessage());
        }
    }

    // ── HITL interactivity ───────────────────────────────────────────────────

    private void handleBlockAction(JsonNode root, String slackUserId) {
        JsonNode action = root.path("actions").path(0);
        String actionId = text(action, "action_id");
        if (actionId == null) return;
        if (actionId.startsWith("next:")) { handleNextAction(root, slackUserId, action, actionId); return; }
        if (!actionId.startsWith("hitl:")) return;
        String[] parts = actionId.split(":");
        if (parts.length != 4) { log.warn("Malformed HITL action_id: {}", actionId); return; }
        String op = parts[1];
        long runId, stepId;
        try { runId = Long.parseLong(parts[2]); stepId = Long.parseLong(parts[3]); }
        catch (NumberFormatException e) { log.warn("HITL action_id had non-numeric ids: {}", actionId); return; }

        Optional<AppUser> who = identity.resolveOrbitUser(slackUserId);
        String responseUrl = text(root, "response_url");
        String triggerId = text(root, "trigger_id");

        if (who.isEmpty()) {
            postEphemeralReply(responseUrl, ":lock: Link your Orbit account first with `/orbit-link <email>`.");
            return;
        }
        AppUser user = who.get();

        switch (op) {
            case "approve" -> applyApprove(runId, stepId, null, user, responseUrl);
            case "reject"  -> openRejectModal(triggerId, runId, stepId);
            case "edit"    -> openEditModal(triggerId, runId, stepId, root);
            default        -> log.warn("Unknown HITL op: {}", op);
        }
    }

    private void handleViewSubmission(JsonNode root, String slackUserId) {
        JsonNode view = root.path("view");
        String callbackId = text(view, "callback_id");
        if (callbackId == null) return;

        Optional<AppUser> who = identity.resolveOrbitUser(slackUserId);
        if (who.isEmpty()) { log.warn("View submission from unlinked user: {}", slackUserId); return; }
        AppUser user = who.get();

        if (snapshots.isOurSubmission(callbackId)) {
            snapshots.handleSubmission(view, user, slackUserId);
            return;
        }
        if (!callbackId.startsWith("hitl_")) return;

        Map<String, String> meta = parseMeta(text(view, "private_metadata"));
        long runId  = Long.parseLong(meta.getOrDefault("runId", "0"));
        long stepId = Long.parseLong(meta.getOrDefault("stepId", "0"));
        String responseUrl = meta.get("responseUrl");

        if ("hitl_reject".equals(callbackId)) {
            String reason = textInput(view, "reject_reason_block", "reason");
            try {
                hitl.reject(runId, stepId, reason == null ? "(no reason given)" : reason, user.getEmail());
                postDecisionUpdate(responseUrl, meta, "REJECTED", user.getEmail(), reason);
            } catch (Exception e) {
                log.warn("HITL reject failed runId={} stepId={}: {}", runId, stepId, e.getMessage());
            }
        } else if ("hitl_edit".equals(callbackId)) {
            String argsJson = textInput(view, "edit_args_block", "args");
            Map<String, Object> args;
            try {
                args = argsJson == null || argsJson.isBlank()
                    ? Map.of()
                    : mapper.readValue(argsJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } catch (Exception e) {
                log.warn("HITL edit args JSON invalid runId={} stepId={}", runId, stepId);
                return;
            }
            applyApprove(runId, stepId, args, user, responseUrl);
        }
    }

    private void applyApprove(long runId, long stepId, Map<String, Object> editedArgs,
                              AppUser user, String responseUrl) {
        try {
            hitl.approve(runId, stepId, editedArgs, user.getEmail());
            postDecisionUpdate(responseUrl, Map.of(), "APPROVED", user.getEmail(), null);
        } catch (Exception e) {
            log.warn("HITL approve failed runId={} stepId={}: {}", runId, stepId, e.getMessage());
            postEphemeralReply(responseUrl, ":x: Approval failed: " + e.getMessage());
        }
    }

    private void openRejectModal(String triggerId, long runId, long stepId) {
        Map<String, Object> view = baseView("hitl_reject", "Reject HITL step", "Reject",
            "reject_reason_block", "reason", "Reason for rejection",
            null, runId, stepId, null);
        slack.openView(triggerId, view);
    }

    private void openEditModal(String triggerId, long runId, long stepId, JsonNode root) {
        // Best-effort: pre-fill with the original args block from the message text.
        // The args are embedded in the second block as a code fence; not always recoverable —
        // empty string is acceptable, approver edits to taste.
        String currentArgs = "{}";
        JsonNode blocks = root.path("message").path("blocks");
        if (blocks.isArray() && blocks.size() >= 2) {
            String txt = blocks.get(1).path("text").path("text").asText("");
            int s = txt.indexOf("```"), e = txt.lastIndexOf("```");
            if (s >= 0 && e > s) currentArgs = txt.substring(s + 3, e).trim();
        }
        Map<String, Object> view = baseView("hitl_edit", "Edit & approve", "Approve",
            "edit_args_block", "args", "Args (JSON)",
            currentArgs, runId, stepId, text(root, "response_url"));
        slack.openView(triggerId, view);
    }

    private Map<String, Object> baseView(String callbackId, String title, String submit,
                                         String blockId, String actionId, String labelText,
                                         String initialValue,
                                         long runId, long stepId, String responseUrl) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (initialValue != null) input.put("initial_value", initialValue);
        input.put("type", "plain_text_input");
        input.put("multiline", true);
        input.put("action_id", actionId);

        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "input");
        block.put("block_id", blockId);
        block.put("element", input);
        block.put("label", Map.of("type", "plain_text", "text", labelText, "emoji", true));

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("type", "modal");
        view.put("callback_id", callbackId);
        view.put("title", Map.of("type", "plain_text", "text", title, "emoji", true));
        view.put("submit", Map.of("type", "plain_text", "text", submit, "emoji", true));
        view.put("close",  Map.of("type", "plain_text", "text", "Cancel", "emoji", true));
        view.put("private_metadata", serialiseMeta(runId, stepId, responseUrl));
        view.put("blocks", List.of(block));
        return view;
    }

    private void postDecisionUpdate(String responseUrl, Map<String, String> meta,
                                    String outcome, String decidedBy, String note) {
        if (responseUrl == null || responseUrl.isBlank()) responseUrl = meta.get("responseUrl");
        if (responseUrl == null || responseUrl.isBlank()) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("replace_original", true);
        body.put("text", "Orbit · HITL " + outcome);
        body.put("blocks", renderer.hitlDecisionCard("", "", outcome, decidedBy, note));
        slack.respondViaUrl(responseUrl, body);
    }

    private void postEphemeralReply(String responseUrl, String msg) {
        if (responseUrl == null || responseUrl.isBlank()) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("response_type", "ephemeral");
        body.put("text", msg);
        slack.respondViaUrl(responseUrl, body);
    }

    private static String textInput(JsonNode view, String blockId, String actionId) {
        JsonNode v = view.path("state").path("values").path(blockId).path(actionId).path("value");
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static String serialiseMeta(long runId, long stepId, String responseUrl) {
        StringBuilder sb = new StringBuilder("runId=").append(runId).append(";stepId=").append(stepId);
        if (responseUrl != null && !responseUrl.isBlank()) {
            sb.append(";responseUrl=").append(responseUrl.replace(";", "%3B"));
        }
        return sb.toString();
    }

    private static Map<String, String> parseMeta(String s) {
        Map<String, String> out = new LinkedHashMap<>();
        if (s == null || s.isBlank()) return out;
        for (String kv : s.split(";")) {
            int eq = kv.indexOf('=');
            if (eq > 0) out.put(kv.substring(0, eq), kv.substring(eq + 1).replace("%3B", ";"));
        }
        return out;
    }

    // ── pipeline ─────────────────────────────────────────────────────────────

    private void respond(String slackUserId, String channel, String threadTs, String userText, Surface surface) {
        Optional<AppUser> orbitUser = identity.resolveOrbitUser(slackUserId);
        if (orbitUser.isEmpty()) {
            sendLinkPrompt(slackUserId, channel, threadTs, surface);
            return;
        }
        String threadKey = conversations.threadKey(channel, threadTs);
        Map<String, Object> priorArgs = conversations.load(threadKey)
            .map(SlackTurnContext::inheritableArgs)
            .orElse(Map.of());
        Optional<ResolvedIntent> intent = intents.resolve(userText, priorArgs);
        if (intent.isPresent() && isInvocation(intent.get()) && surface != Surface.SLASH) {
            invokeWithProgress(orbitUser.get(), channel, threadTs, intent.get(), surface);
            rememberTurn(threadKey, intent.get(), null);
            return;
        }
        List<Map<String, Object>> blocks = executor.execute(intent.orElse(null), orbitUser.get(), surface);
        blocks = renderer.withSuggestions(blocks, executor.suggestionsFor(intent.orElse(null)));
        String fallback = executor.fallbackText(intent.orElse(null));
        send(channel, threadTs, slackUserId, fallback, blocks, surface);
        rememberTurn(threadKey, intent.orElse(null), null);
    }

    private void rememberTurn(String threadKey, ResolvedIntent intent, Long runId) {
        if (intent == null) return;
        Map<String, Object> a = intent.args();
        String project = a.get("projectName") == null ? null : a.get("projectName").toString();
        String severity = a.get("severity") == null ? null : a.get("severity").toString();
        try {
            conversations.save(threadKey, new SlackTurnContext(intent.tool(), project, severity, runId));
        } catch (Exception e) {
            log.warn("SlackConversationStore save failed for {}: {}", threadKey, e.getMessage());
        }
    }

    private void handleNextAction(JsonNode root, String slackUserId, JsonNode action, String actionId) {
        String tool = actionId.substring("next:".length());
        Map<String, Object> args;
        try {
            String value = text(action, "value");
            args = value == null || value.isBlank()
                ? Map.of()
                : mapper.readValue(value, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            log.warn("next-action value JSON invalid: {}", e.getMessage());
            return;
        }
        Optional<AppUser> who = identity.resolveOrbitUser(slackUserId);
        String responseUrl = text(root, "response_url");
        if (who.isEmpty()) {
            postEphemeralReply(responseUrl, ":lock: Link your Orbit account first with `/orbit-link <email>`.");
            return;
        }
        AppUser user = who.get();
        String channel = text(root.path("channel"), "id");
        String threadTs = text(root.path("message"), "thread_ts");
        if (threadTs == null) threadTs = text(root.path("message"), "ts");
        String threadKey = conversations.threadKey(channel, threadTs);

        Map<String, Object> priorArgs = conversations.load(threadKey)
            .map(SlackTurnContext::inheritableArgs).orElse(Map.of());
        Map<String, Object> merged = new LinkedHashMap<>(priorArgs);
        merged.putAll(args);
        ResolvedIntent intent = new ResolvedIntent(tool, merged, "next-action button");

        if (isInvocation(intent)) {
            invokeWithProgress(user, channel, threadTs, intent, Surface.MENTION);
            rememberTurn(threadKey, intent, null);
            return;
        }
        List<Map<String, Object>> blocks = executor.execute(intent, user, Surface.MENTION);
        blocks = renderer.withSuggestions(blocks, executor.suggestionsFor(intent));
        String fallback = executor.fallbackText(intent);
        slack.postInThread(channel, threadTs, fallback, blocks);
        rememberTurn(threadKey, intent, null);
    }

    /**
     * Placeholder-then-update pattern: post "working…", run the agent in this @Async thread,
     * then chat.update the placeholder with the final card. SLASH is excluded because
     * ephemeral messages can't be chat.updated — those keep the synchronous post path.
     */
    private void invokeWithProgress(AppUser user, String channel, String threadTs,
                                    ResolvedIntent intent, Surface surface) {
        String title = invocationTitle(intent.tool());
        List<Map<String, Object>> placeholder = renderer.placeholder(title);
        String placeholderText = "Orbit · " + title + " (working…)";

        Map<String, Object> resp = surface == Surface.DM
            ? slack.postMessage(channel, placeholderText, placeholder)
            : slack.postInThread(channel, threadTs, placeholderText, placeholder);
        String ts = resp == null ? null : (String) resp.get("ts");

        List<Map<String, Object>> finalBlocks = executor.execute(intent, user, surface);
        String fallback = executor.fallbackText(intent);

        if (ts != null && !ts.isEmpty()) {
            slack.updateMessage(channel, ts, fallback, finalBlocks);
        } else {
            log.warn("Slack placeholder post returned no ts (resp={}); falling back to fresh post", resp);
            if (surface == Surface.DM) slack.postMessage(channel, fallback, finalBlocks);
            else slack.postInThread(channel, threadTs, fallback, finalBlocks);
        }
    }

    private boolean isInvocation(ResolvedIntent intent) {
        return intent != null && IntentResolver.INVOCATION_TOOLS.contains(intent.tool());
    }

    private static String invocationTitle(String tool) {
        return switch (tool) {
            case "orbit.run_report"   -> "Report draft";
            case "orbit.run_forecast" -> "Forecast run";
            case "orbit.run_briefing" -> "Delivery briefing";
            default                   -> "Orbit";
        };
    }

    private void sendLinkPrompt(String slackUserId, String channel, String threadTs, Surface surface) {
        String msg = "I don't have your Orbit account linked yet. Use `/orbit-link "
            + internalDomains.exampleEmail() + "` to link.";
        // Always ephemeral / DM-like, not in-thread, to avoid leaking to channel.
        List<Map<String, Object>> blocks = List.of(Map.of(
            "type", "section",
            "text", Map.of("type", "mrkdwn", "text", msg)
        ));
        if (surface == Surface.DM) {
            slack.postMessage(channel, msg, blocks);
        } else {
            slack.postEphemeral(channel, slackUserId, msg, blocks);
        }
    }

    private void send(String channel, String threadTs, String slackUserId, String fallback,
                      List<Map<String, Object>> blocks, Surface surface) {
        switch (surface) {
            case MENTION -> slack.postInThread(channel, threadTs, fallback, blocks);
            case DM      -> slack.postMessage(channel, fallback, blocks);
            case SLASH   -> slack.postEphemeral(channel, slackUserId, fallback, blocks);
        }
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Strip leading "<@UBOTID> " mention so the IntentResolver gets clean text. */
    private static String stripMention(String s) {
        if (s == null) return null;
        return s.replaceFirst("^\\s*<@[^>]+>\\s*", "").trim();
    }

    public enum Surface { SLASH, MENTION, DM }
}
