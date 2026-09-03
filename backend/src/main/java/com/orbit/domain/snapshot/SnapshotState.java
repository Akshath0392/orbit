package com.orbit.domain.snapshot;

public enum SnapshotState {
    PENDING, RUNNING, READY, FAILED;

    public boolean isTerminal() {
        return this == READY || this == FAILED;
    }
}
