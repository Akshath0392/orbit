package com.orbit.integration.slack;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SlackSignatureVerifierTest {

    private final SlackSignatureVerifier verifier = new SlackSignatureVerifier();
    private static final String SECRET = "8f742231b10e8888abcd99yyyzzz85a5";
    private static final String BODY   = "token=xyz&team_id=T1&command=%2Forbit&text=alerts";
    private static final long   NOW    = 1_700_000_000L;

    private static String sign(String secret, String ts, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(("v0:" + ts + ":" + body).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format("%02x", b));
            return "v0=" + sb;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void accepts_correctly_signed_request() {
        String ts = String.valueOf(NOW);
        assertThat(verifier.verify(SECRET, ts, sign(SECRET, ts, BODY), BODY, NOW)).isTrue();
    }

    @Test
    void rejects_tampered_body() {
        String ts = String.valueOf(NOW);
        String sig = sign(SECRET, ts, BODY);
        assertThat(verifier.verify(SECRET, ts, sig, BODY + "&injected=evil", NOW)).isFalse();
    }

    @Test
    void rejects_replayed_request_outside_5_minute_window() {
        String ts = String.valueOf(NOW - 600);
        String sig = sign(SECRET, ts, BODY);
        assertThat(verifier.verify(SECRET, ts, sig, BODY, NOW)).isFalse();
    }

    @Test
    void rejects_wrong_secret() {
        String ts = String.valueOf(NOW);
        String sig = sign("different-secret", ts, BODY);
        assertThat(verifier.verify(SECRET, ts, sig, BODY, NOW)).isFalse();
    }

    @Test
    void rejects_missing_or_blank_inputs() {
        String ts = String.valueOf(NOW);
        String sig = sign(SECRET, ts, BODY);
        assertThat(verifier.verify(null, ts, sig, BODY, NOW)).isFalse();
        assertThat(verifier.verify("", ts, sig, BODY, NOW)).isFalse();
        assertThat(verifier.verify(SECRET, null, sig, BODY, NOW)).isFalse();
        assertThat(verifier.verify(SECRET, ts, null, BODY, NOW)).isFalse();
        assertThat(verifier.verify(SECRET, ts, sig, null, NOW)).isFalse();
    }

    @Test
    void rejects_non_numeric_timestamp() {
        assertThat(verifier.verify(SECRET, "notanumber", "v0=deadbeef", BODY, NOW)).isFalse();
    }
}
