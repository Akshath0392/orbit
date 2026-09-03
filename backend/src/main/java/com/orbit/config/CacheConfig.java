package com.orbit.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * In-process Caffeine caches for the heavy dashboard aggregates.
 *
 * Short TTL (orbit.cache.dashboard-ttl-seconds, default 90s) bounds staleness
 * for data that changes outside the Jira-sync path (alerts, thresholds);
 * {@link com.orbit.service.sync.CacheInvalidationListener} evicts immediately
 * when Jira data changes (sync success, webhook ingest).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String RADAR               = "radar";
    public static final String CLIENTS_LIST        = "clients-list";
    public static final String PORTFOLIO_DASHBOARD = "portfolio-dashboard";

    public static final List<String> DASHBOARD_CACHES =
        List.of(RADAR, CLIENTS_LIST, PORTFOLIO_DASHBOARD);

    @Bean
    public CacheManager cacheManager(
            @Value("${orbit.cache.dashboard-ttl-seconds:90}") long ttlSeconds) {
        CaffeineCacheManager manager = new CaffeineCacheManager(
            DASHBOARD_CACHES.toArray(String[]::new));
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
            .maximumSize(1_000));
        return manager;
    }
}
