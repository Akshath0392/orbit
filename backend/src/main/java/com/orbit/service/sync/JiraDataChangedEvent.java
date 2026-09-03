package com.orbit.service.sync;

/**
 * Published whenever Jira-derived data changes (sync run success, webhook
 * ingest) so dashboard caches can be evicted ahead of their TTL.
 *
 * @param source short label of the mutation path, for logging ("sync", "webhook")
 */
public record JiraDataChangedEvent(String source) {}
