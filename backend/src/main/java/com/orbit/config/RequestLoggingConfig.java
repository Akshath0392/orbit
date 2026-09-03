package com.orbit.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Logs every inbound HTTP request with method, path, status, and duration.
 * Output: [METHOD] /path → STATUS in Xms  (principal if authenticated)
 */
@Component
public class RequestLoggingConfig implements Filter {

    private static final Logger log = LoggerFactory.getLogger("orbit.http");

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String path   = request.getRequestURI();

        // Skip health/static noise
        if (path.startsWith("/swagger") || path.startsWith("/api-docs") || path.equals("/")) {
            chain.doFilter(req, res);
            return;
        }

        try {
            chain.doFilter(req, res);
        } finally {
            long ms     = System.currentTimeMillis() - start;
            int  status = response.getStatus();
            String user = request.getUserPrincipal() != null
                ? " [" + request.getUserPrincipal().getName() + "]" : "";

            if (status >= 500)      log.error("{} {} → {} in {}ms{}", method, path, status, ms, user);
            else if (status >= 400) log.warn ("{} {} → {} in {}ms{}", method, path, status, ms, user);
            else                    log.info ("{} {} → {} in {}ms{}", method, path, status, ms, user);
        }
    }
}
