package com.orbit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.client.AppUser;
import com.orbit.domain.config.SlackConfig;
import com.orbit.domain.slack.SlackMagicLink;
import com.orbit.integration.slack.SlackInteractionRouter;
import com.orbit.integration.slack.SlackSignatureVerifier;
import com.orbit.integration.slack.SlackService;
import com.orbit.repository.AppUserRepository;
import com.orbit.repository.SlackConfigRepository;
import com.orbit.service.slack.MagicLinkService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Slack inbound endpoints. ALL Slack traffic enters here.
 * Signature verification uses the signing secret from slack_config (admin console).
 * All paths ack within 3s; real work is dispatched @Async via SlackInteractionRouter.
 */
@RestController
@RequestMapping("/api/v1/slack")
public class SlackEventController {

    private static final Logger log = LoggerFactory.getLogger(SlackEventController.class);
    private static final String HEADER_TS  = "X-Slack-Request-Timestamp";
    private static final String HEADER_SIG = "X-Slack-Signature";

    private final SlackConfigRepository configs;
    private final SlackSignatureVerifier verifier;
    private final SlackInteractionRouter router;
    private final MagicLinkService magicLinks;
    private final AppUserRepository users;
    private final SlackService slack;
    private final ObjectMapper mapper = new ObjectMapper();

    public SlackEventController(SlackConfigRepository configs,
                                SlackSignatureVerifier verifier,
                                SlackInteractionRouter router,
                                MagicLinkService magicLinks,
                                AppUserRepository users,
                                SlackService slack) {
        this.configs = configs;
        this.verifier = verifier;
        this.router = router;
        this.magicLinks = magicLinks;
        this.users = users;
        this.slack = slack;
    }

    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> events(HttpServletRequest req) throws Exception {
        String body = readBody(req);
        if (!verify(req, body)) return ResponseEntity.status(401).body(Map.of("error", "invalid_signature"));

        JsonNode root = mapper.readTree(body);
        // URL verification handshake — respond inline with challenge.
        if ("url_verification".equals(text(root, "type"))) {
            return ResponseEntity.ok(Map.of("challenge", text(root, "challenge")));
        }
        router.dispatchEvent(body);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/commands", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> commands(HttpServletRequest req) throws Exception {
        String body = readBody(req);
        if (!verify(req, body)) return ResponseEntity.status(401).body(Map.of("error", "invalid_signature"));
        router.dispatchSlashCommand(parseForm(body));
        // Empty 200 = Slack shows nothing; router will follow up via response_url / chat.postMessage.
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/interactions", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> interactions(HttpServletRequest req) throws Exception {
        String body = readBody(req);
        if (!verify(req, body)) return ResponseEntity.status(401).body(Map.of("error", "invalid_signature"));
        String payload = parseForm(body).getOrDefault("payload", "");
        router.dispatchInteraction(payload);
        return ResponseEntity.ok().build();
    }

    /**
     * Magic-link confirmation. Public path (no JWT) — the token IS the auth.
     * On success, binds app_users.slack_user_id and DMs the user.
     */
    @PostMapping(value = "/link/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> confirmLink(@RequestBody Map<String, String> body) {
        String token = body == null ? null : body.get("token");
        Optional<SlackMagicLink> opt = magicLinks.consume(token);
        if (opt.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("ok", false, "error", "invalid_or_expired_token"));
        }
        SlackMagicLink link = opt.get();
        Optional<AppUser> u = users.findByEmail(link.getEmail());
        if (u.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", "user_not_found"));
        }
        AppUser user = u.get();
        user.setSlackUserId(link.getSlackUserId());
        users.save(user);
        slack.sendDm(link.getSlackUserId(),
            ":white_check_mark: Linked to Orbit as `" + link.getEmail() + "`. Try `/orbit alerts critical`.");
        log.info("Slack link confirmed: slackUserId={} email={}", link.getSlackUserId(), link.getEmail());
        return ResponseEntity.ok(Map.of("ok", true, "email", link.getEmail()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean verify(HttpServletRequest req, String body) {
        Optional<SlackConfig> cfg = configs.findFirstByEnabledTrue();
        if (cfg.isEmpty() || cfg.get().getSigningSecret() == null || cfg.get().getSigningSecret().isBlank()) {
            log.warn("Slack inbound rejected: no signing secret configured");
            return false;
        }
        return verifier.verify(cfg.get().getSigningSecret(),
            req.getHeader(HEADER_TS), req.getHeader(HEADER_SIG), body);
    }

    private static String readBody(HttpServletRequest req) throws Exception {
        // Slack signs exact raw bytes; read via input stream rather than getReader/readLine.
        return new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> out = new HashMap<>();
        if (body == null || body.isEmpty()) return out;
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            out.put(k, v);
        }
        return out;
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f); return v == null || v.isNull() ? null : v.asText();
    }
}
