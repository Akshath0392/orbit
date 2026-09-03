package com.orbit.service.snapshot;

import com.orbit.domain.snapshot.SnapshotState;

/** Result returned to the caller after a {@link SnapshotService#request} call. */
public record SnapshotResult(
    Long          id,
    SnapshotState state,
    boolean       fromCache,
    boolean       dedup
) {
    public static SnapshotResult fresh(Long id, SnapshotState s)     { return new SnapshotResult(id, s, false, false); }
    public static SnapshotResult cached(Long id)                     { return new SnapshotResult(id, SnapshotState.READY, true, false); }
    public static SnapshotResult dedupHit(Long id, SnapshotState s)  { return new SnapshotResult(id, s, false, true); }
}
