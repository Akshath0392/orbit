package com.orbit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.client.AppUser;
import com.orbit.domain.snapshot.Snapshot;
import com.orbit.domain.snapshot.SnapshotState;
import com.orbit.repository.AppUserRepository;
import com.orbit.repository.SnapshotRepository;
import com.orbit.security.JwtService;
import com.orbit.service.snapshot.SnapshotResult;
import com.orbit.service.snapshot.SnapshotService;
import com.orbit.service.snapshot.SnapshotStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = SnapshotController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class SnapshotControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean JwtService               jwtService;
    @MockBean SnapshotRepository       snapshots;
    @MockBean SnapshotService          snapshotService;
    @MockBean SnapshotStorageService   storage;
    @MockBean AppUserRepository        users;

    private AppUser owner() {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", 42L);
        u.setEmail("p@orbit.io");
        u.setRole("PM");
        return u;
    }

    private AppUser admin() {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", 1L);
        u.setEmail("admin@orbit.io");
        u.setRole("ADMIN");
        return u;
    }

    private Snapshot snap(long id, long userId, SnapshotState state) {
        Snapshot s = new Snapshot();
        ReflectionTestUtils.setField(s, "id", id);
        s.setUserId(userId);
        s.setKind("RADAR");
        s.setLens("PJM");
        s.setState(state);
        s.setCreatedAt(LocalDateTime.now());
        s.setExpiresAt(LocalDateTime.now().plusDays(7));
        return s;
    }

    private java.security.Principal principal(String email) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            email, "n/a", java.util.List.of());
    }

    @Test
    void post_request_returns_fresh_result() throws Exception {
        when(users.findByEmail("p@orbit.io")).thenReturn(Optional.of(owner()));
        when(snapshotService.request(any(), any()))
            .thenReturn(SnapshotResult.fresh(99L, SnapshotState.PENDING));
        mvc.perform(post("/api/v1/snapshots").principal(principal("p@orbit.io"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "lens", "PJM", "portfolioId", 7, "projectId", 11))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(99))
            .andExpect(jsonPath("$.state").value("PENDING"))
            .andExpect(jsonPath("$.fromCache").value(false));
    }

    @Test
    void post_request_rejects_missing_lens() throws Exception {
        when(users.findByEmail(any())).thenReturn(Optional.of(owner()));
        mvc.perform(post("/api/v1/snapshots").principal(principal("p@orbit.io"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portfolioId\":7}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void status_returns_download_urls_when_ready() throws Exception {
        when(snapshots.findById(99L)).thenReturn(Optional.of(snap(99L, 42L, SnapshotState.READY)));
        when(users.findByEmail("p@orbit.io")).thenReturn(Optional.of(owner()));
        mvc.perform(get("/api/v1/snapshots/99/status").principal(principal("p@orbit.io")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("READY"))
            .andExpect(jsonPath("$.downloadPng").value("/api/v1/snapshots/99/png"))
            .andExpect(jsonPath("$.downloadPdf").value("/api/v1/snapshots/99/pdf"));
    }

    @Test
    void status_returns_eta_when_pending() throws Exception {
        Snapshot s = snap(99L, 42L, SnapshotState.RUNNING);
        when(snapshots.findById(99L)).thenReturn(Optional.of(s));
        when(users.findByEmail("p@orbit.io")).thenReturn(Optional.of(owner()));
        mvc.perform(get("/api/v1/snapshots/99/status").principal(principal("p@orbit.io")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("RUNNING"))
            .andExpect(jsonPath("$.etaSeconds").exists());
    }

    @Test
    void status_returns_error_when_failed() throws Exception {
        Snapshot s = snap(99L, 42L, SnapshotState.FAILED);
        s.setErrorMessage("renderer timeout");
        when(snapshots.findById(99L)).thenReturn(Optional.of(s));
        when(users.findByEmail("p@orbit.io")).thenReturn(Optional.of(owner()));
        mvc.perform(get("/api/v1/snapshots/99/status").principal(principal("p@orbit.io")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("FAILED"))
            .andExpect(jsonPath("$.error").value("renderer timeout"));
    }

    @Test
    void status_404_when_missing() throws Exception {
        when(snapshots.findById(123L)).thenReturn(Optional.empty());
        when(users.findByEmail(any())).thenReturn(Optional.of(owner()));
        mvc.perform(get("/api/v1/snapshots/123/status").principal(principal("p@orbit.io")))
            .andExpect(status().isNotFound());
    }

    @Test
    void status_403_when_different_user() throws Exception {
        Snapshot s = snap(99L, 999L, SnapshotState.READY);   // owned by a different user
        when(snapshots.findById(99L)).thenReturn(Optional.of(s));
        when(users.findByEmail("p@orbit.io")).thenReturn(Optional.of(owner()));
        mvc.perform(get("/api/v1/snapshots/99/status").principal(principal("p@orbit.io")))
            .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_read_other_users_snapshot() throws Exception {
        Snapshot s = snap(99L, 999L, SnapshotState.READY);
        when(snapshots.findById(99L)).thenReturn(Optional.of(s));
        when(users.findByEmail("admin@orbit.io")).thenReturn(Optional.of(admin()));
        mvc.perform(get("/api/v1/snapshots/99/status").principal(principal("admin@orbit.io")))
            .andExpect(status().isOk());
    }

    @Test
    void download_410_when_expired() throws Exception {
        Snapshot s = snap(99L, 42L, SnapshotState.READY);
        s.setExpiresAt(LocalDateTime.now().minusHours(1));
        s.setPngPath("/p/99/snap.png");
        when(snapshots.findById(99L)).thenReturn(Optional.of(s));
        when(users.findByEmail("p@orbit.io")).thenReturn(Optional.of(owner()));
        mvc.perform(get("/api/v1/snapshots/99/png").principal(principal("p@orbit.io")))
            .andExpect(status().isGone());
    }

    @Test
    void download_409_when_not_ready() throws Exception {
        Snapshot s = snap(99L, 42L, SnapshotState.RUNNING);
        when(snapshots.findById(99L)).thenReturn(Optional.of(s));
        when(users.findByEmail("p@orbit.io")).thenReturn(Optional.of(owner()));
        mvc.perform(get("/api/v1/snapshots/99/png").principal(principal("p@orbit.io")))
            .andExpect(status().isConflict());
    }

    @Test
    void download_png_streams_bytes_with_content_disposition() throws Exception {
        Snapshot s = snap(99L, 42L, SnapshotState.READY);
        s.setPngPath("/p/99/snap.png");
        when(snapshots.findById(99L)).thenReturn(Optional.of(s));
        when(users.findByEmail("p@orbit.io")).thenReturn(Optional.of(owner()));
        when(storage.read("/p/99/snap.png")).thenReturn(new byte[]{1, 2, 3});
        mvc.perform(get("/api/v1/snapshots/99/png").principal(principal("p@orbit.io")))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString("orbit-snapshot-99.png")));
    }
}
