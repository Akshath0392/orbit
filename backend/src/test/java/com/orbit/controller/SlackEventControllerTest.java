package com.orbit.controller;

import com.orbit.domain.config.SlackConfig;
import com.orbit.integration.slack.SlackInteractionRouter;
import com.orbit.integration.slack.SlackSignatureVerifier;
import com.orbit.repository.SlackConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = SlackEventController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(SlackSignatureVerifier.class)
class SlackEventControllerTest {

    private static final String SECRET = "test-signing-secret-xyz";

    @Autowired MockMvc mvc;
    @MockBean SlackConfigRepository configs;
    @MockBean SlackInteractionRouter router;
    @MockBean com.orbit.security.JwtService jwt;
    @MockBean com.orbit.repository.AppUserRepository users;
    @MockBean com.orbit.service.slack.MagicLinkService magicLinks;
    @MockBean com.orbit.integration.slack.SlackService slack;

    private SlackConfig enabledCfg() {
        SlackConfig c = new SlackConfig();
        c.setEnabled(true);
        c.setSigningSecret(SECRET);
        return c;
    }

    private static String sign(String body, String ts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(("v0:" + ts + ":" + body).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format("%02x", b));
            return "v0=" + sb;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void url_verification_handshake_returns_challenge() throws Exception {
        when(configs.findFirstByEnabledTrue()).thenReturn(Optional.of(enabledCfg()));
        String body = "{\"type\":\"url_verification\",\"challenge\":\"abc-123\"}";
        String ts   = String.valueOf(System.currentTimeMillis() / 1000);
        mvc.perform(post("/api/v1/slack/events")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Slack-Request-Timestamp", ts)
                .header("X-Slack-Signature", sign(body, ts))
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.challenge").value("abc-123"));
        verifyNoInteractions(router);
    }

    @Test
    void valid_event_dispatches_to_router_async() throws Exception {
        when(configs.findFirstByEnabledTrue()).thenReturn(Optional.of(enabledCfg()));
        String body = "{\"type\":\"event_callback\",\"event\":{\"type\":\"app_mention\",\"user\":\"U1\"}}";
        String ts   = String.valueOf(System.currentTimeMillis() / 1000);
        mvc.perform(post("/api/v1/slack/events")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Slack-Request-Timestamp", ts)
                .header("X-Slack-Signature", sign(body, ts))
                .content(body))
            .andExpect(status().isOk());
        verify(router).dispatchEvent(body);
    }

    @Test
    void invalid_signature_returns_401() throws Exception {
        when(configs.findFirstByEnabledTrue()).thenReturn(Optional.of(enabledCfg()));
        String body = "{\"type\":\"event_callback\"}";
        String ts   = String.valueOf(System.currentTimeMillis() / 1000);
        mvc.perform(post("/api/v1/slack/events")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Slack-Request-Timestamp", ts)
                .header("X-Slack-Signature", "v0=deadbeef")
                .content(body))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(router);
    }

    @Test
    void no_signing_secret_configured_returns_401() throws Exception {
        when(configs.findFirstByEnabledTrue()).thenReturn(Optional.empty());
        String body = "{}";
        String ts   = String.valueOf(System.currentTimeMillis() / 1000);
        mvc.perform(post("/api/v1/slack/events")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Slack-Request-Timestamp", ts)
                .header("X-Slack-Signature", "v0=anything")
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void slash_command_dispatches_parsed_form() throws Exception {
        when(configs.findFirstByEnabledTrue()).thenReturn(Optional.of(enabledCfg()));
        String body = "token=xyz&command=%2Forbit&user_id=U7&channel_id=C9&text=alerts+critical";
        String ts   = String.valueOf(System.currentTimeMillis() / 1000);
        mvc.perform(post("/api/v1/slack/commands")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-Slack-Request-Timestamp", ts)
                .header("X-Slack-Signature", sign(body, ts))
                .content(body))
            .andExpect(status().isOk());
        verify(router).dispatchSlashCommand(argThat(form ->
            "/orbit".equals(form.get("command"))
                && "U7".equals(form.get("user_id"))
                && "alerts critical".equals(form.get("text"))));
    }

    @Test
    void link_confirm_valid_token_binds_slack_user_id() throws Exception {
        com.orbit.domain.slack.SlackMagicLink link = new com.orbit.domain.slack.SlackMagicLink();
        link.setSlackUserId("U7");
        link.setEmail("pjm@orbit.io");
        com.orbit.domain.client.AppUser u = new com.orbit.domain.client.AppUser();
        u.setEmail("pjm@orbit.io");
        when(magicLinks.consume("good-token")).thenReturn(Optional.of(link));
        when(users.findByEmail("pjm@orbit.io")).thenReturn(Optional.of(u));

        mvc.perform(post("/api/v1/slack/link/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"good-token\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.email").value("pjm@orbit.io"));

        verify(users).save(argThat(saved -> "U7".equals(saved.getSlackUserId())));
        verify(slack).sendDm(eq("U7"), anyString());
    }

    @Test
    void link_confirm_invalid_token_returns_400() throws Exception {
        when(magicLinks.consume("bad")).thenReturn(Optional.empty());
        mvc.perform(post("/api/v1/slack/link/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"bad\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_or_expired_token"));
        verifyNoInteractions(slack);
    }

    @Test
    void interaction_extracts_payload_field() throws Exception {
        when(configs.findFirstByEnabledTrue()).thenReturn(Optional.of(enabledCfg()));
        String payloadJson = "{\"type\":\"block_actions\",\"user\":{\"id\":\"U1\"}}";
        String body = "payload=" + java.net.URLEncoder.encode(payloadJson, StandardCharsets.UTF_8);
        String ts   = String.valueOf(System.currentTimeMillis() / 1000);
        mvc.perform(post("/api/v1/slack/interactions")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-Slack-Request-Timestamp", ts)
                .header("X-Slack-Signature", sign(body, ts))
                .content(body))
            .andExpect(status().isOk());
        verify(router).dispatchInteraction(payloadJson);
    }
}
