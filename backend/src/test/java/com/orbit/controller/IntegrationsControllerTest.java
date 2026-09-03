package com.orbit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.config.SlackConfig;
import com.orbit.domain.config.SlackProjectChannel;
import com.orbit.integration.slack.SlackEncryptionService;
import com.orbit.integration.slack.SlackService;
import com.orbit.repository.ProjectRepository;
import com.orbit.repository.SlackConfigRepository;
import com.orbit.repository.SlackProjectChannelRepository;
import com.orbit.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = IntegrationsController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class IntegrationsControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean JwtService                     jwtService;
    @MockBean SlackConfigRepository          slackConfigs;
    @MockBean SlackProjectChannelRepository  slackChannels;
    @MockBean SlackService                   slackService;
    @MockBean ProjectRepository              projects;
    @MockBean SlackEncryptionService         slackEnc;

    @BeforeEach
    void setUpEncryption() {
        // Encryption is a pass-through in tests — don't alter token values
        when(slackEnc.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(slackEnc.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private SlackConfig cfg(String workspace, String token, String channel) {
        SlackConfig c = new SlackConfig();
        c.setWorkspaceName(workspace); c.setBotToken(token);
        c.setDefaultChannel(channel); c.setEnabled(true);
        try { var f = SlackConfig.class.getDeclaredField("id"); f.setAccessible(true); f.set(c, 1L); }
        catch (Exception ignored) {}
        return c;
    }

    private SlackProjectChannel ch(Long projectId, String channelId) {
        SlackProjectChannel c = new SlackProjectChannel();
        c.setProjectId(projectId); c.setChannelId(channelId); c.setChannelName(channelId);
        return c;
    }

    // ── GET /slack ────────────────────────────────────────────────────────────

    @Test
    void getSlackConfigReturnsConfiguredWhenExists() throws Exception {
        when(slackConfigs.findFirstByEnabledTrue())
            .thenReturn(Optional.of(cfg("acme", "xoxb-abc-1234", "#general")));

        mvc.perform(get("/api/v1/admin/integrations/slack"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.workspaceName").value("acme"))
            .andExpect(jsonPath("$.defaultChannel").value("#general"));
    }

    @Test
    void getSlackConfigMasksBotToken() throws Exception {
        when(slackConfigs.findFirstByEnabledTrue())
            .thenReturn(Optional.of(cfg("acme", "xoxb-abc-1234", "#general")));

        mvc.perform(get("/api/v1/admin/integrations/slack"))
            .andExpect(jsonPath("$.botToken").value("***1234"));
    }

    @Test
    void getSlackConfigReturnsNotConfiguredWhenAbsent() throws Exception {
        when(slackConfigs.findFirstByEnabledTrue()).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/admin/integrations/slack"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(false));
    }

    // ── PUT /slack ────────────────────────────────────────────────────────────

    @Test
    void saveSlackConfigCreatesNewRecord() throws Exception {
        when(slackConfigs.findFirstByEnabledTrue()).thenReturn(Optional.empty());
        when(slackConfigs.save(any())).thenReturn(cfg("acme", "xoxb-new-token", "#orbit-alerts"));

        mvc.perform(put("/api/v1/admin/integrations/slack")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "workspaceName", "acme",
                    "botToken", "xoxb-new-token",
                    "defaultChannel", "#orbit-alerts"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.id").value(1));

        verify(slackConfigs).save(any(SlackConfig.class));
    }

    @Test
    void saveSlackConfigDoesNotOverwriteTokenWithMaskedValue() throws Exception {
        SlackConfig existing = cfg("acme", "xoxb-real-token", "#general");
        when(slackConfigs.findFirstByEnabledTrue()).thenReturn(Optional.of(existing));
        when(slackConfigs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(put("/api/v1/admin/integrations/slack")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "workspaceName", "updated-name",
                    "botToken", "***oken"   // masked placeholder — should not replace
                ))))
            .andExpect(status().isOk());

        // Verify saved config still has the real token
        verify(slackConfigs).save(argThat(c -> "xoxb-real-token".equals(c.getBotToken())));
    }

    // ── POST /slack/test ──────────────────────────────────────────────────────

    @Test
    void testSlackReturnsTrueWhenSendSucceeds() throws Exception {
        when(slackConfigs.findFirstByEnabledTrue())
            .thenReturn(Optional.of(cfg("acme", "xoxb-token", "#orbit")));
        when(slackService.sendToChannel(eq("#orbit"), any())).thenReturn(true);

        mvc.perform(post("/api/v1/admin/integrations/slack/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.channel").value("#orbit"));
    }

    @Test
    void testSlackReturnsFalseWhenSendFails() throws Exception {
        when(slackConfigs.findFirstByEnabledTrue())
            .thenReturn(Optional.of(cfg("acme", "xoxb-token", "#orbit")));
        when(slackService.sendToChannel(any(), any())).thenReturn(false);

        mvc.perform(post("/api/v1/admin/integrations/slack/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(false))
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testSlackReturnsBadRequestWhenNotConfigured() throws Exception {
        when(slackConfigs.findFirstByEnabledTrue()).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/admin/integrations/slack/test"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.ok").value(false));
    }

    // ── GET /slack/channels ───────────────────────────────────────────────────

    @Test
    void listChannelsReturnsAllMappings() throws Exception {
        when(slackChannels.findAll()).thenReturn(List.of(
            ch(1L, "C0123ABC"), ch(2L, "C0456DEF")
        ));
        when(projects.findByActiveTrue()).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/integrations/slack/channels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].channelId").value("C0123ABC"))
            .andExpect(jsonPath("$[1].channelId").value("C0456DEF"));
    }

    @Test
    void listChannelsReturnsEmptyWhenNoneMapped() throws Exception {
        when(slackChannels.findAll()).thenReturn(List.of());
        when(projects.findByActiveTrue()).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/integrations/slack/channels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ── PUT /slack/channels/{projectId} ───────────────────────────────────────

    @Test
    void setProjectChannelSavesMapping() throws Exception {
        when(projects.existsById(1L)).thenReturn(true);
        when(slackChannels.findByProjectId(1L)).thenReturn(Optional.empty());
        when(slackChannels.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(put("/api/v1/admin/integrations/slack/channels/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("channelId", "C0123ABCD", "channelName", "#crm-updates"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.projectId").value(1));

        verify(slackChannels).save(any(SlackProjectChannel.class));
    }

    @Test
    void setProjectChannelReturns404ForUnknownProject() throws Exception {
        when(projects.existsById(999L)).thenReturn(false);

        mvc.perform(put("/api/v1/admin/integrations/slack/channels/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("channelId", "C0ABC"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void setProjectChannelUpdatesExistingMapping() throws Exception {
        SlackProjectChannel existing = ch(1L, "C0OLD");
        when(projects.existsById(1L)).thenReturn(true);
        when(slackChannels.findByProjectId(1L)).thenReturn(Optional.of(existing));
        when(slackChannels.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(put("/api/v1/admin/integrations/slack/channels/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("channelId", "C0NEW"))))
            .andExpect(status().isOk());

        verify(slackChannels).save(argThat(c -> "C0NEW".equals(c.getChannelId())));
    }

    // ── DELETE /slack/channels/{projectId} ────────────────────────────────────

    @Test
    void deleteChannelMappingReturns204() throws Exception {
        when(slackChannels.findByProjectId(1L)).thenReturn(Optional.of(ch(1L, "C0123")));

        mvc.perform(delete("/api/v1/admin/integrations/slack/channels/1"))
            .andExpect(status().isNoContent());

        verify(slackChannels).delete(any());
    }

    @Test
    void deleteChannelMappingReturns404WhenNotFound() throws Exception {
        when(slackChannels.findByProjectId(999L)).thenReturn(Optional.empty());

        mvc.perform(delete("/api/v1/admin/integrations/slack/channels/999"))
            .andExpect(status().isNotFound());
    }
}
