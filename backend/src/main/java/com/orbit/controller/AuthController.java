package com.orbit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.client.AppUser;
import com.orbit.repository.AppUserRepository;
import com.orbit.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AppUserRepository users;
    private final JwtService jwt;
    private final PasswordEncoder encoder;
    private final ObjectMapper mapper;

    @Value("${orbit.google.client-id:}") private String googleClientId;
    @Value("${orbit.google.client-secret:}") private String googleClientSecret;
    @Value("${orbit.google.redirect-uri:http://localhost:8080/api/v1/auth/google/callback}") private String googleRedirectUri;
    @Value("${orbit.frontend.url:http://localhost:3000}") private String frontendUrl;
    // Comma-separated list of hosted domains allowed to auto-provision via SSO. Blank = any.
    @Value("${orbit.google.allowed-domains:}") private String googleAllowedDomains;
    // Least-privilege role assigned to a newly SSO-provisioned user (not PM).
    @Value("${orbit.google.default-role:LEADERSHIP}") private String googleDefaultRole;
    // When false, unknown Google users are rejected instead of auto-created.
    @Value("${orbit.google.auto-provision:true}") private boolean googleAutoProvision;
    // Mark auth cookies Secure (set true in prod / behind HTTPS).
    @Value("${orbit.cookie.secure:false}") private boolean cookieSecure;

    private static final String STATE_COOKIE = "orbit_oauth_state";
    // A valid bcrypt hash used to spend equivalent CPU when the user is absent,
    // so login timing does not reveal whether an email exists (L1).
    private static final String DUMMY_HASH =
        "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    public AuthController(AppUserRepository users, JwtService jwt, PasswordEncoder encoder, ObjectMapper mapper) {
        this.users = users; this.jwt = jwt; this.encoder = encoder; this.mapper = mapper;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        AppUser user = users.findByEmail(email).orElse(null);
        // Run bcrypt on a constant hash even when the user is absent so response time
        // does not reveal whether the email exists (login timing oracle, L1).
        boolean ok = (user != null)
            ? encoder.matches(password, user.getPassword())
            : encoder.matches(password, DUMMY_HASH) && false;
        if (!ok) {
            log.warn("Login failed for email: {}", email);
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        String token = jwt.generate(user.getId(), user.getEmail(), user.getRole(),
            Boolean.TRUE.equals(user.getCanEditBudget()));
        log.info("Login success: {} ({})", user.getEmail(), user.getRole());
        return ResponseEntity.ok(Map.of(
            "token", token,
            "user", Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "initials", user.getInitials() != null ? user.getInitials() : "",
                "avatarColor", user.getAvatarColor() != null ? user.getAvatarColor() : "#6366F1"
            )
        ));
    }

    @GetMapping("/google")
    public void googleLogin(HttpServletResponse response) throws IOException {
        if (googleClientId.isBlank()) {
            response.sendRedirect(frontendUrl + "/login?error=google_not_configured");
            return;
        }
        // Anti-forgery state, bound to the callback via an httpOnly cookie (H6).
        String state = UUID.randomUUID().toString().replace("-", "");
        Cookie stateCookie = new Cookie(STATE_COOKIE, state);
        stateCookie.setHttpOnly(true);
        stateCookie.setSecure(cookieSecure);
        stateCookie.setPath("/");
        stateCookie.setMaxAge(300);
        stateCookie.setAttribute("SameSite", "Lax");
        response.addCookie(stateCookie);

        String hdHint = "";
        if (!googleAllowedDomains.isBlank() && !googleAllowedDomains.contains(",")) {
            hdHint = "&hd=" + encode(googleAllowedDomains.trim());
        }
        String url = "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=" + encode(googleClientId) +
            "&redirect_uri=" + encode(googleRedirectUri) +
            "&response_type=code" +
            "&scope=openid+email+profile" +
            "&state=" + encode(state) + hdHint +
            "&access_type=offline";
        response.sendRedirect(url);
    }

    @GetMapping("/google/callback")
    public void googleCallback(@RequestParam(required = false) String code,
                               @RequestParam(required = false) String state,
                               @RequestParam(required = false) String error,
                               HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        if (error != null || code == null) {
            response.sendRedirect(frontendUrl + "/login?error=google_denied");
            return;
        }
        // Validate anti-forgery state against the cookie set on /google, then clear it.
        String expectedState = readCookie(request, STATE_COOKIE);
        clearStateCookie(response);
        if (state == null || expectedState == null || !constantTimeEquals(state, expectedState)) {
            log.warn("Google OAuth: state parameter missing or mismatched — rejecting callback");
            response.sendRedirect(frontendUrl + "/login?error=state_mismatch");
            return;
        }
        try {
            String tokenBody = "code=" + encode(code) +
                "&client_id=" + encode(googleClientId) +
                "&client_secret=" + encode(googleClientSecret) +
                "&redirect_uri=" + encode(googleRedirectUri) +
                "&grant_type=authorization_code";

            HttpClient http = HttpClient.newHttpClient();
            String tokenJson = http.send(
                HttpRequest.newBuilder().uri(URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(tokenBody)).build(),
                HttpResponse.BodyHandlers.ofString()
            ).body();

            String idToken = mapper.readTree(tokenJson).path("id_token").asText();
            if (idToken.isBlank()) throw new RuntimeException("Missing id_token from Google");

            String infoJson = http.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken))
                    .GET().build(),
                HttpResponse.BodyHandlers.ofString()
            ).body();

            JsonNode info = mapper.readTree(infoJson);
            String email = info.path("email").asText();
            String name  = info.path("name").asText();
            boolean verified = info.path("email_verified").asBoolean(false);
            String hd = info.path("hd").asText("");

            if (!verified || email.isBlank()) {
                response.sendRedirect(frontendUrl + "/login?error=unverified");
                return;
            }

            AppUser existing = users.findByEmail(email).orElse(null);
            AppUser user;
            if (existing != null) {
                user = existing;                       // already vetted — allow login
            } else {
                // New identity: gate provisioning on the domain allowlist + policy,
                // and assign a least-privilege role (H1) rather than PM.
                if (!isDomainAllowed(email, hd)) {
                    log.warn("Google SSO: domain not allowed for {} — rejecting", email);
                    response.sendRedirect(frontendUrl + "/login?error=domain_not_allowed");
                    return;
                }
                if (!googleAutoProvision) {
                    log.warn("Google SSO: auto-provision disabled — rejecting new user {}", email);
                    response.sendRedirect(frontendUrl + "/login?error=not_provisioned");
                    return;
                }
                AppUser u = new AppUser();
                u.setEmail(email);
                u.setName(name);
                String[] parts = name.split(" ");
                u.setInitials(
                    (parts[0].length() > 0 ? String.valueOf(parts[0].charAt(0)) : "") +
                    (parts.length > 1 && parts[parts.length - 1].length() > 0 ? String.valueOf(parts[parts.length - 1].charAt(0)) : "")
                );
                u.setRole(googleDefaultRole);
                u.setAvatarColor("#087f7a");
                u.setPassword(encoder.encode(UUID.randomUUID().toString()));
                user = users.save(u);
                log.info("Google SSO provisioned new user {} with role {}", email, googleDefaultRole);
            }

            String orbitToken = jwt.generate(user.getId(), user.getEmail(), user.getRole(),
                Boolean.TRUE.equals(user.getCanEditBudget()));

            String params = "token=" + encode(orbitToken) +
                "&id=" + user.getId() +
                "&name=" + encode(user.getName() != null ? user.getName() : "") +
                "&email=" + encode(user.getEmail()) +
                "&role=" + encode(user.getRole() != null ? user.getRole() : googleDefaultRole) +
                "&initials=" + encode(user.getInitials() != null ? user.getInitials() : "") +
                "&avatarColor=" + encode(user.getAvatarColor() != null ? user.getAvatarColor() : "#087f7a");

            log.info("Google SSO login: {} ({})", user.getEmail(), user.getRole());
            // Deliver the token in the URL fragment (not the query string) so it is
            // not sent to the server, logged, or leaked via Referer (H6).
            response.sendRedirect(frontendUrl + "/login#" + params);

        } catch (Exception e) {
            log.error("Google OAuth callback failed: {}", e.getMessage(), e);
            response.sendRedirect(frontendUrl + "/login?error=oauth_failed");
        }
    }

    private static String encode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    /** True if the SSO identity's domain (or Google hosted-domain claim) is allowed to provision. */
    private boolean isDomainAllowed(String email, String hd) {
        if (googleAllowedDomains.isBlank()) return true;   // unconfigured — rely on least-priv default role
        String domain = email.contains("@") ? email.substring(email.indexOf('@') + 1).toLowerCase() : "";
        String hostedDomain = hd == null ? "" : hd.toLowerCase();
        for (String d : googleAllowedDomains.split(",")) {
            String allowed = d.trim().toLowerCase();
            if (!allowed.isEmpty() && (allowed.equals(domain) || allowed.equals(hostedDomain))) return true;
        }
        return false;
    }

    private static String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private void clearStateCookie(HttpServletResponse response) {
        Cookie c = new Cookie(STATE_COOKIE, "");
        c.setHttpOnly(true);
        c.setSecure(cookieSecure);
        c.setPath("/");
        c.setMaxAge(0);
        response.addCookie(c);
    }

    /** Constant-time string comparison to avoid a timing oracle on the state token. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
