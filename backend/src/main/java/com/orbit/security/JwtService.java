package com.orbit.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @Value("${orbit.jwt.access-token-expiry-ms}")
    private long accessExpiry;

    private final Environment environment;

    private PrivateKey privateKey;
    private PublicKey  publicKey;

    public JwtService(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void initKeys() throws Exception {
        String privB64 = System.getenv("ORBIT_JWT_PRIVATE_KEY");
        String pubB64  = System.getenv("ORBIT_JWT_PUBLIC_KEY");
        if (privB64 != null && pubB64 != null) {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privB64.strip())));
            publicKey  = kf.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pubB64.strip())));
            log.info("JWT: loaded RS256 key pair from environment variables");
        } else {
            // Never fall back to ephemeral keys in production — that silently invalidates
            // all tokens on restart and breaks multi-instance deploys (audit H5).
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException(
                    "JWT: ORBIT_JWT_PRIVATE_KEY / ORBIT_JWT_PUBLIC_KEY must be set in the prod profile");
            }
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            privateKey = kp.getPrivate();
            publicKey  = kp.getPublic();
            log.warn("JWT: ORBIT_JWT_PRIVATE_KEY / ORBIT_JWT_PUBLIC_KEY not set — using generated RSA key pair. "
                + "All tokens will be invalidated on restart. Set these env vars for production.");
        }
    }

    public String generate(long userId, String email, String role, boolean canEditBudget) {
        return Jwts.builder()
            .subject(email)
            .claims(Map.of("userId", userId, "role", role, "canEditBudget", canEditBudget))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessExpiry))
            .signWith(privateKey)
            .compact();
    }

    /**
     * Mint a short-lived token with extra claims (e.g. {@code scope=snapshot:read}). Used by
     * the snapshot agent to authenticate the headless-browser sidecar against the frontend.
     */
    public String generateShortLived(long userId, String email, String role,
                                     long ttlMs, Map<String, Object> extraClaims) {
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("canEditBudget", false);
        if (extraClaims != null) claims.putAll(extraClaims);
        return Jwts.builder()
            .subject(email)
            .claims(claims)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + ttlMs))
            .signWith(privateKey)
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(publicKey).build()
            .parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token) {
        try { parse(token); return true; }
        catch (JwtException | IllegalArgumentException e) { return false; }
    }

    public String getEmail(String token)  { return parse(token).getSubject(); }
    public String getRole(String token)   { return parse(token).get("role", String.class); }
    public Long   getUserId(String token) { return parse(token).get("userId", Long.class); }
    public String getScope(String token)  { return parse(token).get("scope", String.class); }
    public boolean canEditBudget(String token) {
        Object v = parse(token).get("canEditBudget");
        return Boolean.TRUE.equals(v);
    }
}
