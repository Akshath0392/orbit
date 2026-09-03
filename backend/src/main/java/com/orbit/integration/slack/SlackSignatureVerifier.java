package com.orbit.integration.slack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@Component
public class SlackSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(SlackSignatureVerifier.class);
    private static final long REPLAY_WINDOW_SECONDS = 5 * 60;
    private static final String VERSION = "v0";

    public boolean verify(String signingSecret, String timestampHeader, String slackSignature, String rawBody) {
        return verify(signingSecret, timestampHeader, slackSignature, rawBody, Instant.now().getEpochSecond());
    }

    boolean verify(String signingSecret, String timestampHeader, String slackSignature, String rawBody, long nowEpoch) {
        if (signingSecret == null || signingSecret.isBlank()
            || timestampHeader == null || slackSignature == null || rawBody == null) {
            return false;
        }
        long ts;
        try { ts = Long.parseLong(timestampHeader); }
        catch (NumberFormatException e) { return false; }
        if (Math.abs(nowEpoch - ts) > REPLAY_WINDOW_SECONDS) {
            log.warn("Slack signature replay window exceeded: now={} ts={}", nowEpoch, ts);
            return false;
        }
        String base = VERSION + ":" + timestampHeader + ":" + rawBody;
        String computed = VERSION + "=" + hmacHex(signingSecret, base);
        return MessageDigest.isEqual(
            computed.getBytes(StandardCharsets.UTF_8),
            slackSignature.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
