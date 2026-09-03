package com.orbit.service.snapshot;

import com.orbit.domain.client.AppUser;
import com.orbit.security.JwtService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Mints short-TTL JWTs for the headless-browser sidecar. Token carries a
 * {@code scope=snapshot:read} claim so downstream code can distinguish snapshot-context
 * traffic from a normal user session if we ever need to.
 */
@Service
public class SnapshotJwtService {

    /** 5 minutes — well beyond worst-case render time, short enough that a leaked token is harmless. */
    public static final long TTL_MS = 5 * 60 * 1000L;

    public static final String SCOPE = "snapshot:read";

    private final JwtService jwtService;

    public SnapshotJwtService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public String mintFor(AppUser user) {
        return jwtService.generateShortLived(
            user.getId(),
            user.getEmail(),
            user.getRole(),
            TTL_MS,
            Map.of("scope", SCOPE)
        );
    }
}
