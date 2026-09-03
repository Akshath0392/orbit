package com.orbit.service.snapshot;

import com.orbit.domain.client.AppUser;
import com.orbit.domain.snapshot.Snapshot;
import com.orbit.domain.snapshot.SnapshotState;
import com.orbit.repository.SnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SnapshotServiceTest {

    SnapshotRepository repo;
    RadarSnapshotAgent agent;
    SnapshotService service;
    AppUser user;

    @BeforeEach
    void setUp() {
        repo = mock(SnapshotRepository.class);
        agent = mock(RadarSnapshotAgent.class);
        service = new SnapshotService(repo, agent, 300);
        user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setEmail("p@orbit.io");
    }

    private SnapshotArgs args() {
        return new SnapshotArgs("RADAR", 7L, "PJM", 11L);
    }

    @Test
    void fingerprint_is_deterministic_and_user_scoped() {
        String a = SnapshotService.fingerprint(42L, args());
        String b = SnapshotService.fingerprint(42L, args());
        String c = SnapshotService.fingerprint(43L, args());
        assertThat(a).isEqualTo(b).hasSize(16);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void fresh_request_persists_pending_and_enqueues_agent() {
        when(repo.findReadySince(any(), any())).thenReturn(List.of());
        when(repo.saveAndFlush(any(Snapshot.class))).thenAnswer(inv -> {
            Snapshot s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 99L);
            return s;
        });
        SnapshotResult r = service.request(user, args());
        assertThat(r.id()).isEqualTo(99L);
        assertThat(r.state()).isEqualTo(SnapshotState.PENDING);
        assertThat(r.fromCache()).isFalse();
        assertThat(r.dedup()).isFalse();

        ArgumentCaptor<Snapshot> cap = ArgumentCaptor.forClass(Snapshot.class);
        verify(repo).saveAndFlush(cap.capture());
        assertThat(cap.getValue().getDedupKey()).hasSize(16);
        assertThat(cap.getValue().getState()).isEqualTo(SnapshotState.PENDING);
        verify(agent).renderAsync(99L);
    }

    @Test
    void cache_hit_returns_ready_without_inserting() {
        Snapshot cached = new Snapshot();
        ReflectionTestUtils.setField(cached, "id", 55L);
        cached.setState(SnapshotState.READY);
        cached.setCompletedAt(LocalDateTime.now().minusMinutes(2));
        when(repo.findReadySince(any(), any())).thenReturn(List.of(cached));

        SnapshotResult r = service.request(user, args());
        assertThat(r.id()).isEqualTo(55L);
        assertThat(r.state()).isEqualTo(SnapshotState.READY);
        assertThat(r.fromCache()).isTrue();
        verify(repo, never()).saveAndFlush(any(Snapshot.class));
        verify(agent, never()).renderAsync(anyLong());
    }

    @Test
    void dedup_hit_returns_existing_inflight_row() {
        when(repo.findReadySince(any(), any())).thenReturn(List.of());
        when(repo.saveAndFlush(any(Snapshot.class)))
            .thenThrow(new DataIntegrityViolationException("uq_snapshot_inflight"));
        Snapshot existing = new Snapshot();
        ReflectionTestUtils.setField(existing, "id", 77L);
        existing.setState(SnapshotState.RUNNING);
        when(repo.findInflight(any())).thenReturn(List.of(existing));

        SnapshotResult r = service.request(user, args());
        assertThat(r.id()).isEqualTo(77L);
        assertThat(r.state()).isEqualTo(SnapshotState.RUNNING);
        assertThat(r.dedup()).isTrue();
        verify(agent, never()).renderAsync(anyLong());
    }

    @Test
    void request_rejects_null_user() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> service.request(null, args()));
    }
}
