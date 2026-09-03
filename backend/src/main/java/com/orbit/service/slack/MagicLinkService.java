package com.orbit.service.slack;

import com.orbit.domain.slack.SlackMagicLink;
import com.orbit.repository.SlackMagicLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues and consumes one-shot tokens used by /orbit-link to bind a Slack user
 * to an Orbit AppUser. Tokens expire after orbit.slack.magic-link-ttl-minutes
 * and may be consumed exactly once.
 */
@Service
public class MagicLinkService {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);
    private static final SecureRandom RNG = new SecureRandom();

    private final SlackMagicLinkRepository repo;
    private final long ttlMinutes;

    public MagicLinkService(SlackMagicLinkRepository repo,
                            @Value("${orbit.slack.magic-link-ttl-minutes:15}") long ttlMinutes) {
        this.repo = repo;
        this.ttlMinutes = ttlMinutes;
    }

    @Transactional
    public SlackMagicLink issue(String slackUserId, String email) {
        SlackMagicLink link = new SlackMagicLink();
        link.setToken(newToken());
        link.setSlackUserId(slackUserId);
        link.setEmail(email);
        link.setCreatedAt(LocalDateTime.now());
        link.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
        return repo.save(link);
    }

    /**
     * Consume a token. Returns the link iff token exists, is not expired,
     * and has not been consumed. Marks consumed_at on success.
     */
    @Transactional
    public Optional<SlackMagicLink> consume(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return repo.findByToken(token).flatMap(link -> {
            if (link.getConsumedAt() != null) {
                log.warn("MagicLink token already consumed: slackUserId={}", link.getSlackUserId());
                return Optional.empty();
            }
            if (LocalDateTime.now().isAfter(link.getExpiresAt())) {
                log.warn("MagicLink token expired: slackUserId={}", link.getSlackUserId());
                return Optional.empty();
            }
            link.setConsumedAt(LocalDateTime.now());
            repo.save(link);
            return Optional.of(link);
        });
    }

    private static String newToken() {
        byte[] bytes = new byte[36];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
