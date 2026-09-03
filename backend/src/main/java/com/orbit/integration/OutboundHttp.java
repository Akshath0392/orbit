package com.orbit.integration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Factory for RestTemplates used on outbound sync calls (Jira, HRMS connectors).
 * A bare {@code new RestTemplate()} has infinite connect/read timeouts, so a
 * single stalled response hangs the calling thread forever — fatal for the
 * async backfill worker and for @Scheduled jobs, which share one scheduler
 * thread. With timeouts set, a stall surfaces as ResourceAccessException and
 * the run fails cleanly; resumable cursors make the retry safe.
 */
public final class OutboundHttp {

    private OutboundHttp() {}

    public static RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(60_000);
        return new RestTemplate(factory);
    }
}
