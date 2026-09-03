package com.orbit.service.slack;

import com.orbit.config.InternalEmailDomains;
import com.orbit.domain.client.AppUser;
import com.orbit.domain.slack.SlackMagicLink;
import com.orbit.integration.slack.SlackService;
import com.orbit.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SlackLinkCommandHandlerTest {

    MagicLinkService links;
    AppUserRepository users;
    SlackService slack;
    SlackLinkCommandHandler handler;

    @BeforeEach
    void setUp() {
        links = mock(MagicLinkService.class);
        users = mock(AppUserRepository.class);
        slack = mock(SlackService.class);
        handler = new SlackLinkCommandHandler(links, users, slack,
            new InternalEmailDomains(""), "https://orbit.local/");
    }

    private static SlackMagicLink fakeLink() {
        SlackMagicLink l = new SlackMagicLink();
        l.setToken("tok-abc");
        l.setSlackUserId("U1");
        l.setEmail("pjm@orbit.io");
        l.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        return l;
    }

    @Test
    void issues_link_and_dms_url_for_known_email() {
        when(users.findByEmail("pjm@orbit.io")).thenReturn(Optional.of(new AppUser()));
        when(links.issue("U1", "pjm@orbit.io")).thenReturn(fakeLink());

        handler.handle("U1", "pjm@orbit.io");

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(slack).sendDm(eq("U1"), msg.capture());
        assertThat(msg.getValue()).contains("https://orbit.local/slack/link?token=tok-abc", "15 minutes");
    }

    @Test
    void rejects_malformed_email_with_usage_hint() {
        handler.handle("U1", "not-an-email");
        verify(slack).sendDm(eq("U1"), contains("/orbit-link you@example.com"));
        verifyNoInteractions(links);
    }

    @Test
    void usage_hint_derives_example_from_first_configured_domain() {
        handler = new SlackLinkCommandHandler(links, users, slack,
            new InternalEmailDomains("acme.io, other.io"), "https://orbit.local/");
        handler.handle("U1", "not-an-email");
        verify(slack).sendDm(eq("U1"), contains("/orbit-link your.name@acme.io"));
        verifyNoInteractions(links);
    }

    @Test
    void rejects_non_internal_domain_when_domains_configured() {
        handler = new SlackLinkCommandHandler(links, users, slack,
            new InternalEmailDomains("acme.io"), "https://orbit.local/");
        handler.handle("U1", "pjm@elsewhere.io");
        verify(slack).sendDm(eq("U1"), contains("company email"));
        verifyNoInteractions(links);
    }

    @Test
    void allows_configured_internal_domain() {
        handler = new SlackLinkCommandHandler(links, users, slack,
            new InternalEmailDomains("orbit.io"), "https://orbit.local/");
        when(users.findByEmail("pjm@orbit.io")).thenReturn(Optional.of(new AppUser()));
        when(links.issue("U1", "pjm@orbit.io")).thenReturn(fakeLink());
        handler.handle("U1", "pjm@orbit.io");
        verify(slack).sendDm(eq("U1"), contains("token=tok-abc"));
    }

    @Test
    void unknown_email_returns_generic_response_without_issuing_link() {
        when(users.findByEmail("nope@orbit.io")).thenReturn(Optional.empty());
        handler.handle("U1", "nope@orbit.io");
        verify(slack).sendDm(eq("U1"), contains("If `nope@orbit.io`"));
        verifyNoInteractions(links);
    }

    @Test
    void trims_and_lowercases_email_input() {
        when(users.findByEmail("pjm@orbit.io")).thenReturn(Optional.of(new AppUser()));
        when(links.issue("U1", "pjm@orbit.io")).thenReturn(fakeLink());
        handler.handle("U1", "  PJM@orbit.IO  ");
        verify(links).issue("U1", "pjm@orbit.io");
    }
}
