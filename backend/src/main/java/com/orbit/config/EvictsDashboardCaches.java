package com.orbit.config;

import org.springframework.cache.annotation.CacheEvict;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Clears every dashboard cache after an admin mutation that feeds them
 * (client/project/portfolio CRUD, thresholds, health weights, stages). Jira
 * sync paths evict via {@link com.orbit.service.sync.JiraDataChangedEvent}
 * instead; alert-engine writes rely on the 90s TTL (documented in lld
 * §caching).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@CacheEvict(cacheNames = {CacheConfig.RADAR, CacheConfig.CLIENTS_LIST, CacheConfig.PORTFOLIO_DASHBOARD},
            allEntries = true)
public @interface EvictsDashboardCaches {}
