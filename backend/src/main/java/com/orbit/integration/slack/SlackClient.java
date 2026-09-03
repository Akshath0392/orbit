package com.orbit.integration.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.repository.SlackConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Block Kit-aware Slack API wrapper. Built on chat.postMessage / chat.postEphemeral / chat.update.
 * Bot token sourced from the encrypted slack_config row (admin console).
 * Plain-text only paths still live on {@link SlackService} — this client is for block payloads.
 *
 * The package-private {@link #post(String, Map)} method is the single HTTP path; tests override it.
 */
@Component
public class SlackClient {

    private static final Logger log = LoggerFactory.getLogger(SlackClient.class);
    private static final String API_BASE = "https://slack.com/api/";

    private final SlackConfigRepository configRepo;
    private final SlackEncryptionService slackEnc;
    private final ObjectMapper mapper = new ObjectMapper();

    public SlackClient(SlackConfigRepository configRepo, SlackEncryptionService slackEnc) {
        this.configRepo = configRepo;
        this.slackEnc = slackEnc;
    }

    /** Public, persistent message. Returns {ok, ts, channel, error?}. */
    public Map<String, Object> postMessage(String channel, String fallbackText, List<Map<String, Object>> blocks) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", channel);
        body.put("text", fallbackText);
        body.put("blocks", blocks);
        return post("chat.postMessage", body);
    }

    /** Only-visible-to-one-user message; requires {@code chat:write} + {@code commands} scopes. */
    public Map<String, Object> postEphemeral(String channel, String userId, String fallbackText, List<Map<String, Object>> blocks) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", channel);
        body.put("user", userId);
        body.put("text", fallbackText);
        body.put("blocks", blocks);
        return post("chat.postEphemeral", body);
    }

    /** Edit a previously-posted message by ts. */
    public Map<String, Object> updateMessage(String channel, String ts, String fallbackText, List<Map<String, Object>> blocks) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", channel);
        body.put("ts", ts);
        body.put("text", fallbackText);
        body.put("blocks", blocks);
        return post("chat.update", body);
    }

    /** Reply inside a thread. */
    public Map<String, Object> postInThread(String channel, String threadTs, String fallbackText, List<Map<String, Object>> blocks) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", channel);
        body.put("thread_ts", threadTs);
        body.put("text", fallbackText);
        body.put("blocks", blocks);
        return post("chat.postMessage", body);
    }

    /** Open a modal (HITL reject reason / edit args). */
    public Map<String, Object> openView(String triggerId, Map<String, Object> view) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trigger_id", triggerId);
        body.put("view", view);
        return post("views.open", body);
    }

    /**
     * Send an interactivity response back to Slack via the per-payload response_url.
     * Response URL accepts the same body as chat.postMessage (text + blocks) plus
     * the special {@code replace_original} / {@code response_type} keys. Overridable in tests.
     */
    public Map<String, Object> respondViaUrl(String responseUrl, Map<String, Object> body) {
        try {
            HttpURLConnection http = (HttpURLConnection) new URI(responseUrl).toURL().openConnection();
            http.setRequestMethod("POST");
            http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            http.setDoOutput(true);
            try (OutputStream os = http.getOutputStream()) {
                os.write(mapper.writeValueAsBytes(body));
            }
            int code = http.getResponseCode();
            if (code >= 200 && code < 300) return Map.of("ok", true);
            log.warn("Slack response_url POST returned {}", code);
            return Map.of("ok", false, "error", "http_" + code);
        } catch (Exception e) {
            log.warn("Slack response_url POST threw: {}", e.getMessage());
            return Map.of("ok", false, "error", e.getMessage() != null ? e.getMessage() : "io_error");
        }
    }

    // ── single HTTP path (overridable in tests) ─────────────────────────────

    Map<String, Object> post(String endpoint, Map<String, Object> body) {
        Optional<com.orbit.domain.config.SlackConfig> cfg = configRepo.findFirstByEnabledTrue();
        if (cfg.isEmpty()) return Map.of("ok", false, "error", "slack_not_configured");
        try {
            HttpURLConnection http = (HttpURLConnection) new URI(API_BASE + endpoint).toURL().openConnection();
            http.setRequestMethod("POST");
            http.setRequestProperty("Authorization", "Bearer " + slackEnc.decrypt(cfg.get().getBotToken()));
            http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            http.setDoOutput(true);
            try (OutputStream os = http.getOutputStream()) {
                os.write(mapper.writeValueAsBytes(body));
            }
            String resp = new String(http.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode node = mapper.readTree(resp);
            boolean ok = node.path("ok").asBoolean(false);
            if (!ok) {
                String error = node.path("error").asText("unknown_error");
                log.warn("Slack {} failed: {}", endpoint, error);
                return Map.of("ok", false, "error", error);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("channel", node.path("channel").asText(""));
            out.put("ts", node.path("ts").asText(""));
            return out;
        } catch (Exception e) {
            log.warn("Slack {} threw: {}", endpoint, e.getMessage());
            return Map.of("ok", false, "error", e.getMessage() != null ? e.getMessage() : "connection_error");
        }
    }
}
