package com.orbit.service.slack;

import com.orbit.domain.client.AppUser;
import com.orbit.repository.AppUserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SlackIdentityServiceTest {

    @Test
    void resolves_orbit_user_by_slack_user_id() {
        AppUserRepository repo = mock(AppUserRepository.class);
        AppUser u = new AppUser();
        u.setEmail("pjm@orbit.io");
        u.setSlackUserId("U99");
        when(repo.findBySlackUserId("U99")).thenReturn(Optional.of(u));

        SlackIdentityService svc = new SlackIdentityService(repo);
        assertThat(svc.resolveOrbitUser("U99")).isPresent().get().extracting(AppUser::getEmail).isEqualTo("pjm@orbit.io");
    }

    @Test
    void returns_empty_for_unknown_or_blank_slack_id() {
        AppUserRepository repo = mock(AppUserRepository.class);
        when(repo.findBySlackUserId("Uxx")).thenReturn(Optional.empty());
        SlackIdentityService svc = new SlackIdentityService(repo);
        assertThat(svc.resolveOrbitUser("Uxx")).isEmpty();
        assertThat(svc.resolveOrbitUser(null)).isEmpty();
        assertThat(svc.resolveOrbitUser("")).isEmpty();
        verify(repo, never()).findBySlackUserId(null);
        verify(repo, never()).findBySlackUserId("");
    }
}
