package com.orbit.service.snapshot;

import com.orbit.domain.client.AppUser;
import com.orbit.domain.snapshot.Snapshot;
import com.orbit.domain.snapshot.SnapshotState;
import com.orbit.repository.SnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Coalescing front-door for snapshot requests. Three result shapes:
 *  - {@link SnapshotResult#cached}    — a recent READY row served as cache (no new render)
 *  - {@link SnapshotResult#dedupHit}  — an identical request is already PENDING/RUNNING
 *  - {@link SnapshotResult#fresh}     — newly persisted PENDING row, render enqueued
 *
 * Concurrency safety: the partial unique index {@code uq_snapshot_inflight} (see V79)
 * serialises concurrent submits at the DB layer. The catch block converts the resulting
 * {@link DataIntegrityViolationException} into a dedup-hit lookup.
 */
@Service
public class SnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);

    private final SnapshotRepository snapshots;
    private final RadarSnapshotAgent agent;
    private final long cacheTtlSeconds;

    public SnapshotService(SnapshotRepository snapshots,
                           RadarSnapshotAgent agent,
                           @Value("${snapshot.cache-ttl-seconds:300}") long cacheTtlSeconds) {
        this.snapshots = snapshots;
        this.agent = agent;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public SnapshotResult request(AppUser user, SnapshotArgs args) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("snapshot request requires a logged-in user");
        }
        String key = fingerprint(user.getId(), args);
        LocalDateTime cacheCutoff = LocalDateTime.now().minusSeconds(cacheTtlSeconds);

        List<Snapshot> readyList = snapshots.findReadySince(key, cacheCutoff);
        if (!readyList.isEmpty()) {
            Snapshot cached = readyList.get(0);
            log.info("snapshot cache hit key={} id={}", key, cached.getId());
            return SnapshotResult.cached(cached.getId());
        }

        Snapshot row = new Snapshot();
        row.setUserId(user.getId());
        row.setDedupKey(key);
        row.setKind(args.kind());
        row.setPortfolioId(args.portfolioId());
        row.setLens(args.lens());
        row.setProjectId(args.projectId());
        row.setState(SnapshotState.PENDING);
        row.setCreatedAt(LocalDateTime.now());
        row.setExpiresAt(LocalDateTime.now().plusDays(7));

        try {
            Snapshot saved = snapshots.saveAndFlush(row);
            agent.renderAsync(saved.getId());
            log.info("snapshot queued key={} id={}", key, saved.getId());
            return SnapshotResult.fresh(saved.getId(), SnapshotState.PENDING);
        } catch (DataIntegrityViolationException e) {
            List<Snapshot> inflight = snapshots.findInflight(key);
            if (inflight.isEmpty()) {
                throw new IllegalStateException("dedup race: in-flight row vanished for key=" + key, e);
            }
            Snapshot existing = inflight.get(0);
            log.info("snapshot dedup hit key={} existing id={} state={}", key, existing.getId(), existing.getState());
            return SnapshotResult.dedupHit(existing.getId(), existing.getState());
        }
    }

    static String fingerprint(Long userId, SnapshotArgs args) {
        String raw = String.join(":",
            String.valueOf(userId),
            args.portfolioId() == null ? "0" : args.portfolioId().toString(),
            args.lens(),
            args.projectId()   == null ? "0" : args.projectId().toString(),
            args.kind()
        );
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
