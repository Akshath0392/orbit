package com.orbit.service.slack;

import com.orbit.domain.client.AppUser;
import com.orbit.repository.AppUserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Looks up the Orbit AppUser bound to a Slack user_id.
 * Binding is established via /orbit-link → MagicLinkService.consume().
 */
@Service
public class SlackIdentityService {

    private final AppUserRepository users;

    public SlackIdentityService(AppUserRepository users) {
        this.users = users;
    }

    public Optional<AppUser> resolveOrbitUser(String slackUserId) {
        if (slackUserId == null || slackUserId.isBlank()) return Optional.empty();
        return users.findBySlackUserId(slackUserId);
    }
}
