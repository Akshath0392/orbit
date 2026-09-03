package com.orbit.integration.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.repository.AppUserRepository;
import com.orbit.repository.SlackConfigRepository;
import com.orbit.repository.SlackProjectChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@Service
public class SlackService {

    private static final Logger log = LoggerFactory.getLogger(SlackService.class);

    private final SlackConfigRepository configRepo;
    private final SlackProjectChannelRepository channelRepo;
    private final SlackEncryptionService slackEnc;
    private final AppUserRepository userRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SlackService(SlackConfigRepository configRepo,
                        SlackProjectChannelRepository channelRepo,
                        SlackEncryptionService slackEnc,
                        AppUserRepository userRepo) {
        this.configRepo = configRepo;
        this.channelRepo = channelRepo;
        this.slackEnc = slackEnc;
        this.userRepo = userRepo;
    }

    /**
     * Post a message to a Slack channel.
     * Checks the Slack API "ok" field in the response body — HTTP 200 with ok:false is treated as failure.
     * Returns a result map: {ok, channel, ts, error}
     */
    public Map<String, Object> sendToChannelDetailed(String channelId, String message) {
        return configRepo.findFirstByEnabledTrue().map(cfg -> {
            try {
                URI uri = new URI("https://slack.com/api/chat.postMessage");
                HttpURLConnection http = (HttpURLConnection) uri.toURL().openConnection();
                http.setRequestMethod("POST");
                http.setRequestProperty("Authorization", "Bearer " + slackEnc.decrypt(cfg.getBotToken()));
                http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                http.setDoOutput(true);

                String body = "{\"channel\":\"" + channelId + "\",\"text\":"
                    + objectMapper.writeValueAsString(message) + "}";
                try (OutputStream os = http.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }

                String responseBody = new String(http.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode node = objectMapper.readTree(responseBody);
                boolean ok = node.path("ok").asBoolean(false);

                if (!ok) {
                    String error = node.path("error").asText("unknown_error");
                    log.warn("Slack chat.postMessage failed: {} (channel={})", error, channelId);
                    return Map.<String, Object>of("ok", false, "channel", channelId, "error", error);
                }

                log.info("Slack message sent to {} ts={}", channelId, node.path("ts").asText());
                return Map.<String, Object>of("ok", true, "channel", channelId,
                    "ts", node.path("ts").asText(""));

            } catch (Exception e) {
                log.warn("Slack send failed: {}", e.getMessage());
                return Map.<String, Object>of("ok", false, "channel", channelId,
                    "error", e.getMessage() != null ? e.getMessage() : "connection_error");
            }
        }).orElse(Map.of("ok", false, "channel", channelId, "error", "slack_not_configured"));
    }

    /** Convenience wrapper — returns true only if Slack confirmed ok:true. */
    public boolean sendToChannel(String channelId, String message) {
        return Boolean.TRUE.equals(sendToChannelDetailed(channelId, message).get("ok"));
    }

    /** Resolve the project-specific Slack channel ID, or fall back to the default channel. */
    public Optional<String> resolveChannel(Long projectId) {
        if (projectId != null) {
            Optional<String> projectChannel = channelRepo.findByProjectId(projectId)
                .map(c -> c.getChannelId());
            if (projectChannel.isPresent()) return projectChannel;
        }
        return getDefaultChannel();
    }

    /** Returns the default channel ID from the active Slack config. */
    public Optional<String> getDefaultChannel() {
        return configRepo.findFirstByEnabledTrue()
            .map(cfg -> cfg.getDefaultChannel())
            .filter(ch -> ch != null && !ch.isBlank());
    }

    /** Send a Slack DM to a user by their Slack user ID. */
    public boolean sendDm(String slackUserId, String message) {
        return sendToChannel(slackUserId, message);
    }

    /**
     * Resolve a Slack user ID from an email via users.lookupByEmail.
     * Returns empty if Slack is not configured or the user is not found.
     */
    public Optional<String> resolveSlackUserId(String email) {
        Optional<String> cached = userRepo.findByEmail(email)
            .map(u -> u.getSlackUserId())
            .filter(s -> s != null && !s.isBlank());
        if (cached.isPresent()) return cached;

        Optional<String> looked = configRepo.findFirstByEnabledTrue().flatMap(cfg -> {
            try {
                String encoded = java.net.URLEncoder.encode(email, StandardCharsets.UTF_8);
                URI uri = new URI("https://slack.com/api/users.lookupByEmail?email=" + encoded);
                HttpURLConnection http = (HttpURLConnection) uri.toURL().openConnection();
                http.setRequestProperty("Authorization", "Bearer " + slackEnc.decrypt(cfg.getBotToken()));
                if (http.getResponseCode() == 200) {
                    String body = new String(http.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    JsonNode node = objectMapper.readTree(body);
                    if (node.path("ok").asBoolean()) {
                        return Optional.of(node.path("user").path("id").asText());
                    }
                }
            } catch (Exception e) {
                log.warn("Slack user lookup failed for {}: {}", email, e.getMessage());
            }
            return Optional.empty();
        });

        looked.ifPresent(slackId -> userRepo.findByEmail(email).ifPresent(u -> {
            try {
                u.setSlackUserId(slackId);
                userRepo.save(u);
            } catch (Exception e) {
                log.warn("Failed to persist slack_user_id for {}: {}", email, e.getMessage());
            }
        }));
        return looked;
    }
}
