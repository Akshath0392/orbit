package com.orbit.controller;

import com.orbit.domain.config.SlackConfig;
import com.orbit.domain.config.SlackProjectChannel;
import com.orbit.integration.slack.SlackEncryptionService;
import com.orbit.integration.slack.SlackService;
import com.orbit.repository.ProjectRepository;
import com.orbit.repository.SlackConfigRepository;
import com.orbit.repository.SlackProjectChannelRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/integrations")
public class IntegrationsController {

    private final SlackConfigRepository slackConfigs;
    private final SlackProjectChannelRepository slackChannels;
    private final SlackService slackService;
    private final ProjectRepository projects;
    private final SlackEncryptionService slackEnc;

    public IntegrationsController(SlackConfigRepository slackConfigs,
                                  SlackProjectChannelRepository slackChannels,
                                  SlackService slackService,
                                  ProjectRepository projects,
                                  SlackEncryptionService slackEnc) {
        this.slackConfigs = slackConfigs;
        this.slackChannels = slackChannels;
        this.slackService = slackService;
        this.projects = projects;
        this.slackEnc = slackEnc;
    }

    // ── GET /slack — current slack config (bot_token masked) ─────────────────

    @GetMapping("/slack")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getSlackConfig() {
        return slackConfigs.findFirstByEnabledTrue()
            .map(cfg -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", cfg.getId());
                m.put("workspaceName", cfg.getWorkspaceName() != null ? cfg.getWorkspaceName() : "");
                m.put("botToken", maskToken(cfg.getBotToken()));
                m.put("defaultChannel", cfg.getDefaultChannel() != null ? cfg.getDefaultChannel() : "");
                m.put("enabled", Boolean.TRUE.equals(cfg.getEnabled()));
                m.put("configured", true);
                return ResponseEntity.ok((Object) m);
            })
            .orElse(ResponseEntity.ok(Map.of("configured", false)));
    }

    // ── PUT /slack — save/update slack config ─────────────────────────────────

    @PutMapping("/slack")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> saveSlackConfig(@RequestBody Map<String, Object> body) {
        SlackConfig cfg = slackConfigs.findFirstByEnabledTrue()
            .orElse(new SlackConfig());

        if (body.containsKey("workspaceName")) cfg.setWorkspaceName((String) body.get("workspaceName"));
        if (body.containsKey("botToken") && body.get("botToken") != null) {
            String token = (String) body.get("botToken");
            if (!token.isBlank() && !token.startsWith("***")) {
                cfg.setBotToken(slackEnc.encrypt(token));
            }
        }
        if (body.containsKey("signingSecret") && body.get("signingSecret") != null) {
            String secret = (String) body.get("signingSecret");
            if (!secret.isBlank() && !secret.startsWith("***")) {
                cfg.setSigningSecret(secret);
            }
        }
        if (body.containsKey("defaultChannel")) cfg.setDefaultChannel((String) body.get("defaultChannel"));

        cfg.setEnabled(true);
        if (cfg.getCreatedAt() == null) cfg.setCreatedAt(LocalDateTime.now());

        SlackConfig saved = slackConfigs.save(cfg);
        return ResponseEntity.ok(Map.of("ok", true, "id", saved.getId()));
    }

    // ── POST /slack/test — send test message ──────────────────────────────────

    @PostMapping("/slack/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> testSlack() {
        return slackConfigs.findFirstByEnabledTrue().map(cfg -> {
            String channel = cfg.getDefaultChannel() != null ? cfg.getDefaultChannel() : "general";
            boolean sent = slackService.sendToChannel(channel,
                "Orbit test message — Slack integration is configured and working.");
            if (sent) {
                return ResponseEntity.ok((Object) Map.of("ok", true, "channel", channel));
            } else {
                return ResponseEntity.ok((Object) Map.of("ok", false,
                    "error", "Slack API call failed — check bot token and channel"));
            }
        }).orElse(ResponseEntity.badRequest().body(
            Map.of("ok", false, "error", "Slack is not configured")));
    }

    // ── GET /slack/channels — project channel mappings ────────────────────────

    @GetMapping("/slack/channels")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> listChannels() {
        List<SlackProjectChannel> channels = slackChannels.findAll();
        Map<Long, String> projectNames = projects.findByActiveTrue().stream()
            .collect(Collectors.toMap(p -> p.getId(), p -> p.getName()));

        return channels.stream().map(ch -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("projectId", ch.getProjectId());
            m.put("projectName", projectNames.getOrDefault(ch.getProjectId(), "Unknown"));
            m.put("channelId", ch.getChannelId() != null ? ch.getChannelId() : "");
            m.put("channelName", ch.getChannelName() != null ? ch.getChannelName() : "");
            return m;
        }).collect(Collectors.toList());
    }

    // ── PUT /slack/channels/{projectId} — set channel for project ─────────────

    @PutMapping("/slack/channels/{projectId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> setProjectChannel(@PathVariable Long projectId,
                                               @RequestBody Map<String, Object> body) {
        if (!projects.existsById(projectId)) {
            return ResponseEntity.notFound().build();
        }
        SlackProjectChannel ch = slackChannels.findByProjectId(projectId)
            .orElse(new SlackProjectChannel());
        ch.setProjectId(projectId);
        if (body.containsKey("channelId")) ch.setChannelId((String) body.get("channelId"));
        if (body.containsKey("channelName")) ch.setChannelName((String) body.get("channelName"));
        slackChannels.save(ch);
        return ResponseEntity.ok(Map.of("ok", true, "projectId", projectId));
    }

    // ── DELETE /slack/channels/{projectId} — remove channel mapping ───────────

    @DeleteMapping("/slack/channels/{projectId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteProjectChannel(@PathVariable Long projectId) {
        return slackChannels.findByProjectId(projectId).map(ch -> {
            slackChannels.delete(ch);
            return ResponseEntity.noContent().<Object>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String maskToken(String token) {
        if (token == null || token.length() <= 4) return "****";
        return "***" + token.substring(token.length() - 4);
    }
}
