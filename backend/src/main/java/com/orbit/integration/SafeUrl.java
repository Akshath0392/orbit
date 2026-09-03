package com.orbit.integration;

import java.net.InetAddress;
import java.net.URI;

/**
 * Validates user-supplied outbound base URLs to prevent SSRF (audit M6).
 *
 * <p>An admin-set integration base URL (Jira / HRMS) is used for server-side
 * HTTP calls, so it must not be allowed to point at internal/loopback/link-local
 * addresses. This requires {@code https} and rejects hosts that resolve to a
 * private, loopback, link-local, wildcard, or multicast address.
 */
public final class SafeUrl {

    private SafeUrl() {}

    public static void validatePublicHttps(String url) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("URL is required");
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("URL must use https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL host is required");
        }
        InetAddress[] addrs;
        try {
            addrs = InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new IllegalArgumentException("URL host does not resolve");
        }
        for (InetAddress a : addrs) {
            if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress()
                    || a.isSiteLocalAddress() || a.isMulticastAddress()) {
                throw new IllegalArgumentException("URL host resolves to a private/loopback address");
            }
        }
    }
}
