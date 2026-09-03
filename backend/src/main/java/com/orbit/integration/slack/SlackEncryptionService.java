package com.orbit.integration.slack;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class SlackEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(SlackEncryptionService.class);
    private static final String ALGO       = "AES/GCM/NoPadding";
    private static final int    TAG_BITS   = 128;
    private static final int    IV_BYTES   = 12;
    private static final String ENC_PREFIX = "enc:";

    private final Environment environment;
    private SecretKey secretKey;

    public SlackEncryptionService(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void init() throws Exception {
        String keyStr = System.getenv("SLACK_TOKEN_ENC_KEY");
        if (keyStr != null && !keyStr.isBlank()) {
            // Accept the key as hex (e.g. 64 hex chars = 32 bytes) or base64. AES accepts
            // only 128/192/256-bit keys — fail fast on a misprovisioned key instead of
            // logging a misleading "AES-256" and silently using a bad length (audit M8).
            byte[] raw = decodeKey(keyStr.strip());
            if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
                throw new IllegalStateException(
                    "SLACK_TOKEN_ENC_KEY must be a 16/24/32-byte key (hex or base64); decoded to "
                    + raw.length + " bytes");
            }
            secretKey = new SecretKeySpec(raw, "AES");
            log.info("SlackEncryptionService: loaded AES-{} key from SLACK_TOKEN_ENC_KEY", raw.length * 8);
        } else {
            // Never fall back to an ephemeral key in prod — stored tokens would become
            // undecryptable after a restart.
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("SLACK_TOKEN_ENC_KEY must be set in the prod profile");
            }
            KeyGenerator gen = KeyGenerator.getInstance("AES");
            gen.init(256);
            secretKey = gen.generateKey();
            log.warn("SECURITY: SLACK_TOKEN_ENC_KEY not set — using generated AES key. "
                + "Encrypted Slack tokens will be unreadable after restart.");
        }
    }

    /**
     * Decode the configured key as hex or base64. A pure-hex string of 32/48/64 chars
     * is treated as hex (16/24/32 bytes); anything else is decoded as base64.
     */
    private static byte[] decodeKey(String s) {
        if (s.matches("[0-9a-fA-F]+") && (s.length() == 32 || s.length() == 48 || s.length() == 64)) {
            return HexFormat.of().parseHex(s);
        }
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("SLACK_TOKEN_ENC_KEY is not valid hex or base64");
        }
    }

    /** Encrypt plaintext token; returns ENC_PREFIX + base64(iv + ciphertext). Already-encrypted values pass through. */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.startsWith(ENC_PREFIX)) return plaintext;
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[IV_BYTES + ct.length];
            System.arraycopy(iv, 0, combined, 0, IV_BYTES);
            System.arraycopy(ct, 0, combined, IV_BYTES, ct.length);
            return ENC_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // Fail closed — never persist an unencrypted token because encryption failed (M8).
            log.error("SlackEncryptionService.encrypt failed: {}", e.getMessage());
            throw new IllegalStateException("Slack token encryption failed", e);
        }
    }

    /** Decrypt a value produced by encrypt(). Plaintext (no prefix) is returned as-is for migration. */
    public String decrypt(String value) {
        if (value == null || !value.startsWith(ENC_PREFIX)) return value;
        try {
            byte[] combined = Base64.getDecoder().decode(value.substring(ENC_PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            byte[] ct = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Fail closed — a decrypt failure must not surface ciphertext as if it were
            // a usable token (M8). The deliberate plaintext-passthrough for pre-encryption
            // migration is handled above by the ENC_PREFIX check.
            log.error("SlackEncryptionService.decrypt failed: {}", e.getMessage());
            throw new IllegalStateException("Slack token decryption failed", e);
        }
    }
}
