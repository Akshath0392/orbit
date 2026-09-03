package com.orbit.service.slack;

import com.orbit.domain.slack.SlackMagicLink;
import com.orbit.repository.SlackMagicLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MagicLinkServiceTest {

    SlackMagicLinkRepository repo;
    MagicLinkService svc;

    @BeforeEach
    void setUp() {
        repo = mock(SlackMagicLinkRepository.class);
        when(repo.save(any(SlackMagicLink.class))).thenAnswer(inv -> inv.getArgument(0));
        svc = new MagicLinkService(repo, 15);
    }

    @Test
    void issue_persists_unique_token_with_15_minute_ttl() {
        SlackMagicLink a = svc.issue("U1", "p@orbit.io");
        SlackMagicLink b = svc.issue("U1", "p@orbit.io");
        assertThat(a.getToken()).isNotBlank().hasSizeGreaterThanOrEqualTo(40);
        assertThat(a.getToken()).isNotEqualTo(b.getToken());
        long minutes = java.time.Duration.between(a.getCreatedAt(), a.getExpiresAt()).toMinutes();
        assertThat(minutes).isEqualTo(15);
        verify(repo, times(2)).save(any(SlackMagicLink.class));
    }

    @Test
    void consume_valid_token_returns_link_and_marks_consumed() {
        SlackMagicLink link = new SlackMagicLink();
        link.setToken("tok-valid");
        link.setSlackUserId("U1");
        link.setEmail("p@orbit.io");
        link.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(repo.findByToken("tok-valid")).thenReturn(Optional.of(link));

        Optional<SlackMagicLink> out = svc.consume("tok-valid");

        assertThat(out).isPresent();
        assertThat(link.getConsumedAt()).isNotNull();
        verify(repo).save(link);
    }

    @Test
    void consume_already_consumed_token_returns_empty() {
        SlackMagicLink link = new SlackMagicLink();
        link.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        link.setConsumedAt(LocalDateTime.now().minusMinutes(1));
        when(repo.findByToken("tok-used")).thenReturn(Optional.of(link));

        assertThat(svc.consume("tok-used")).isEmpty();
    }

    @Test
    void consume_expired_token_returns_empty() {
        SlackMagicLink link = new SlackMagicLink();
        link.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(repo.findByToken("tok-exp")).thenReturn(Optional.of(link));

        assertThat(svc.consume("tok-exp")).isEmpty();
    }

    @Test
    void consume_unknown_or_blank_token_returns_empty() {
        when(repo.findByToken("nope")).thenReturn(Optional.empty());
        assertThat(svc.consume("nope")).isEmpty();
        assertThat(svc.consume(null)).isEmpty();
        assertThat(svc.consume("")).isEmpty();
    }
}
