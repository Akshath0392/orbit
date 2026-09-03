package com.orbit.service.snapshot;

import com.orbit.domain.client.AppUser;
import com.orbit.domain.snapshot.Snapshot;
import com.orbit.domain.snapshot.SnapshotState;
import com.orbit.repository.AppUserRepository;
import com.orbit.repository.SnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RadarSnapshotAgentTest {

    SnapshotRepository snapshots;
    AppUserRepository users;
    SnapshotJwtService jwt;
    SnapshotRendererClient renderer;
    SnapshotStorageService storage;
    RadarSnapshotAgent agent;
    AppUser user;
    Snapshot row;

    @BeforeEach
    void setUp() {
        snapshots = mock(SnapshotRepository.class);
        users     = mock(AppUserRepository.class);
        jwt       = mock(SnapshotJwtService.class);
        renderer  = mock(SnapshotRendererClient.class);
        storage   = mock(SnapshotStorageService.class);
        agent = new RadarSnapshotAgent(snapshots, users, jwt, renderer, storage,
            "http://localhost:3000", 20000);

        user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setEmail("p@orbit.io");
        user.setRole("PM");

        row = new Snapshot();
        ReflectionTestUtils.setField(row, "id", 99L);
        row.setUserId(42L);
        row.setLens("PJM");
        row.setPortfolioId(7L);
        row.setProjectId(11L);
        row.setKind("RADAR");
        row.setState(SnapshotState.PENDING);
        row.setCreatedAt(LocalDateTime.now());

        when(snapshots.findById(99L)).thenReturn(Optional.of(row));
        when(users.findById(42L)).thenReturn(Optional.of(user));
        when(jwt.mintFor(user)).thenReturn("jwt-token");
    }

    @Test
    void render_success_writes_paths_and_sets_ready() {
        when(renderer.render(any())).thenReturn(
            new SnapshotRendererClient.RenderResult(new byte[]{1}, new byte[]{2}, 1234L));
        when(storage.save(eqLong(99L), any(), any()))
            .thenReturn(new SnapshotStorageService.Stored("/p/99/snap.png", "/p/99/snap.pdf"));

        agent.render(99L);

        ArgumentCaptor<Snapshot> cap = ArgumentCaptor.forClass(Snapshot.class);
        verify(snapshots, atLeast(2)).save(cap.capture());
        Snapshot last = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertThat(last.getState()).isEqualTo(SnapshotState.READY);
        assertThat(last.getPngPath()).isEqualTo("/p/99/snap.png");
        assertThat(last.getPdfPath()).isEqualTo("/p/99/snap.pdf");
        assertThat(last.getCompletedAt()).isNotNull();
    }

    @Test
    void render_target_url_includes_snapshot_flag_lens_portfolio_project() {
        when(renderer.render(any())).thenReturn(
            new SnapshotRendererClient.RenderResult(new byte[]{1}, new byte[]{2}, 0L));
        when(storage.save(anyLong(), any(), any()))
            .thenReturn(new SnapshotStorageService.Stored("/x.png", "/x.pdf"));

        agent.render(99L);

        ArgumentCaptor<SnapshotRendererClient.RenderRequest> cap =
            ArgumentCaptor.forClass(SnapshotRendererClient.RenderRequest.class);
        verify(renderer).render(cap.capture());
        String url = cap.getValue().targetUrl();
        assertThat(url).contains("/radar?snapshot=1", "lens=PJM", "portfolio=7", "project=11");
    }

    @Test
    void renderer_throwing_marks_failed_not_thrown() {
        when(renderer.render(any())).thenThrow(new RuntimeException("sidecar boom"));

        agent.render(99L);

        ArgumentCaptor<Snapshot> cap = ArgumentCaptor.forClass(Snapshot.class);
        verify(snapshots, atLeast(2)).save(cap.capture());
        Snapshot last = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertThat(last.getState()).isEqualTo(SnapshotState.FAILED);
        assertThat(last.getErrorMessage()).contains("sidecar boom");
    }

    @Test
    void renderer_empty_payload_marks_failed() {
        when(renderer.render(any())).thenReturn(
            new SnapshotRendererClient.RenderResult(null, null, 0L));

        agent.render(99L);

        ArgumentCaptor<Snapshot> cap = ArgumentCaptor.forClass(Snapshot.class);
        verify(snapshots, atLeast(2)).save(cap.capture());
        Snapshot last = cap.getAllValues().get(cap.getAllValues().size() - 1);
        assertThat(last.getState()).isEqualTo(SnapshotState.FAILED);
        assertThat(last.getErrorMessage()).contains("empty payload");
    }

    @Test
    void missing_snapshot_id_is_a_noop() {
        when(snapshots.findById(123L)).thenReturn(Optional.empty());
        agent.render(123L);
        verify(renderer, never()).render(any());
        verify(storage, never()).save(anyLong(), any(), any());
    }

    private static long eqLong(long v) { return org.mockito.ArgumentMatchers.eq(v); }
}
