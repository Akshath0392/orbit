package com.orbit.service.sync;

import com.orbit.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Clears the dashboard caches when Jira-derived data changes. */
@Component
public class CacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationListener.class);

    private final CacheManager cacheManager;

    public CacheInvalidationListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @EventListener
    public void onJiraDataChanged(JiraDataChangedEvent event) {
        for (String name : CacheConfig.DASHBOARD_CACHES) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
        log.debug("Dashboard caches cleared (source={})", event.source());
    }
}
