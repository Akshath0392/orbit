package com.orbit.service.snapshot;

import com.orbit.domain.snapshot.Snapshot;
import com.orbit.domain.snapshot.SnapshotState;
import com.orbit.repository.SnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Marks abandoned PENDING/RUNNING snapshots as FAILED — frees their slot in the partial
 * unique index {@code uq_snapshot_inflight} so retries can proceed. Runs every 30 s.
 *
 * Triggered when the sidecar crashes between starting the row and writing the result, or
 * when the renderer hangs past {@code timeout-ms} without throwing.
 */
@Service
public class SnapshotWatchdog {

    private static final Logger log = LoggerFactory.getLogger(SnapshotWatchdog.class);

    private final SnapshotRepository snapshots;
    private final long stuckCutoffSeconds;

    public SnapshotWatchdog(SnapshotRepository snapshots,
                            @Value("${snapshot.stuck-cutoff-seconds:60}") long stuckCutoffSeconds) {
        this.snapshots = snapshots;
        this.stuckCutoffSeconds = stuckCutoffSeconds;
    }

    @Scheduled(fixedDelayString = "${snapshot.watchdog-interval-ms:30000}")
    public void sweep() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(stuckCutoffSeconds);
        List<Snapshot> stuck = snapshots.findStuck(cutoff);
        if (stuck.isEmpty()) return;
        for (Snapshot s : stuck) {
            s.setState(SnapshotState.FAILED);
            s.setErrorMessage("watchdog: stuck in " + (s.getState() == null ? "?" : s.getState().name())
                + " for > " + stuckCutoffSeconds + "s");
            s.setCompletedAt(LocalDateTime.now());
            snapshots.save(s);
            log.warn("snapshot {} marked FAILED by watchdog (created {})", s.getId(), s.getCreatedAt());
        }
    }
}
