package com.orbit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Snapshot-scoped JWTs (minted by SnapshotJwtService for the Playwright sidecar)
 * must be elevated to ROLE_ADMIN on safe HTTP methods so that any linked Slack
 * user can render the full Radar page for any lens — without granting write
 * access. These tests pin the elevation rules.
 */
class JwtFilterSnapshotScopeTest {

    JwtService jwt;
    JwtFilter filter;

    @BeforeEach
    void setUp() {
        jwt = mock(JwtService.class);
        filter = new JwtFilter(jwt);
        SecurityContextHolder.clearContext();
    }

    @Test
    void snapshot_scope_get_on_radar_path_elevates_to_admin() throws Exception {
        primeToken("ENGINEERING", "snapshot:read");
        runFilter("GET", "/api/v1/dashboard/radar");

        var authority = currentAuthorities();
        assertThat(authority).contains("ROLE_ENGINEERING", "ROLE_ADMIN");
    }

    @Test
    void snapshot_scope_get_on_sensitive_path_does_not_elevate() throws Exception {
        // H3: the elevation is scoped to the Radar data surface — it must NOT unlock
        // admin/HR endpoints even on a GET.
        primeToken("ENGINEERING", "snapshot:read");
        runFilter("GET", "/api/v1/admin/users");

        var authority = currentAuthorities();
        assertThat(authority).contains("ROLE_ENGINEERING").doesNotContain("ROLE_ADMIN");
    }

    @Test
    void snapshot_scope_post_does_not_elevate() throws Exception {
        primeToken("ENGINEERING", "snapshot:read");
        runFilter("POST", "/api/v1/dashboard/radar");

        var authority = currentAuthorities();
        assertThat(authority).contains("ROLE_ENGINEERING").doesNotContain("ROLE_ADMIN");
    }

    @Test
    void no_scope_token_does_not_elevate() throws Exception {
        primeToken("PM", null);
        runFilter("GET", "/api/v1/dashboard/radar");

        var authority = currentAuthorities();
        assertThat(authority).contains("ROLE_PM").doesNotContain("ROLE_ADMIN");
    }

    @Test
    void different_scope_does_not_elevate() throws Exception {
        primeToken("ENGINEERING", "report:read");
        runFilter("GET", "/api/v1/dashboard/radar");

        var authority = currentAuthorities();
        assertThat(authority).contains("ROLE_ENGINEERING").doesNotContain("ROLE_ADMIN");
    }

    private void primeToken(String role, String scope) {
        when(jwt.isValid("tok")).thenReturn(true);
        when(jwt.getRole("tok")).thenReturn(role);
        when(jwt.getEmail("tok")).thenReturn("u@orbit.io");
        when(jwt.getScope("tok")).thenReturn(scope);
    }

    private void runFilter(String method, String path) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer tok");
        when(req.getMethod()).thenReturn(method);
        when(req.getRequestURI()).thenReturn(path);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        ReflectionTestUtils.invokeMethod(filter, "doFilterInternal", req, res, chain);
    }

    private java.util.Set<String> currentAuthorities() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        assertThat(a).isNotNull();
        return a.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
    }
}
