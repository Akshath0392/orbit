package com.orbit.service.snapshot;

import com.orbit.domain.snapshot.Snapshot;
import com.orbit.domain.snapshot.SnapshotState;
import com.orbit.repository.SnapshotRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SnapshotWatchdogTest {

    @Test
    void stuck_rows_marked_failed_with_watchdog_message() {
        SnapshotRepository repo = mock(SnapshotRepository.class);
        Snapshot stuck = new Snapshot();
        ReflectionTestUtils.setField(stuck, "id", 5L);
        stuck.setState(SnapshotState.RUNNING);
        stuck.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        when(repo.findStuck(any())).thenReturn(List.of(stuck));

        new SnapshotWatchdog(repo, 60).sweep();

        ArgumentCaptor<Snapshot> cap = ArgumentCaptor.forClass(Snapshot.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getState()).isEqualTo(SnapshotState.FAILED);
        assertThat(cap.getValue().getErrorMessage()).contains("watchdog");
        assertThat(cap.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    void no_stuck_rows_is_noop() {
        SnapshotRepository repo = mock(SnapshotRepository.class);
        when(repo.findStuck(any())).thenReturn(List.of());
        new SnapshotWatchdog(repo, 60).sweep();
        verify(repo, never()).save(any(Snapshot.class));
    }
}
