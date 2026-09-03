package com.orbit.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final String SNAPSHOT_SCOPE = "snapshot:read";

    /**
     * Path prefixes the snapshot-scoped token may read with elevated authority.
     * This is exactly the Radar dashboard's data surface — it deliberately EXCLUDES
     * /admin/** (user PII) and /hrms/** (HR PII), so a captured snapshot token
     * cannot exfiltrate sensitive endpoints (audit H3).
     */
    private static final List<String> SNAPSHOT_ELEVATED_PREFIXES = List.of(
        "/api/v1/dashboard/",
        "/api/v1/am/",
        "/api/v1/portfolios",
        "/api/v1/clients",
        "/api/v1/accounts/",
        "/api/v1/cr/",
        "/api/v1/bugs/",
        "/api/v1/capacity/",
        "/api/v1/man-days/",
        "/api/v1/jira-sync/backfill-status"
    );

    private final JwtService jwtService;

    public JwtFilter(JwtService jwtService) { this.jwtService = jwtService; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isValid(token)) {
                String role = jwtService.getRole(token);
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

                // Snapshot-scoped JWTs (5-min TTL, minted by SnapshotJwtService) are used
                // by the Playwright sidecar to render the live Radar page on behalf of any
                // user. The token is elevated to ADMIN-level read ONLY for GETs to the
                // Radar data surface (SNAPSHOT_ELEVATED_PREFIXES) — never for /admin/**,
                // /hrms/** or any other endpoint — so a captured token cannot exfiltrate
                // PII/HR data. Mutating verbs never receive the elevation.
                if (SNAPSHOT_SCOPE.equals(jwtService.getScope(token))
                        && "GET".equalsIgnoreCase(req.getMethod())
                        && isSnapshotElevatablePath(req.getRequestURI())) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                }

                var auth = new UsernamePasswordAuthenticationToken(
                    jwtService.getEmail(token), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(req, res);
    }

    private static boolean isSnapshotElevatablePath(String path) {
        if (path == null) return false;
        for (String prefix : SNAPSHOT_ELEVATED_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }
}
