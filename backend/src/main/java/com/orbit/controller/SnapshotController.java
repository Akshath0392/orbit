package com.orbit.controller;

import com.orbit.domain.client.AppUser;
import com.orbit.domain.snapshot.Snapshot;
import com.orbit.domain.snapshot.SnapshotState;
import com.orbit.repository.AppUserRepository;
import com.orbit.repository.SnapshotRepository;
import com.orbit.service.snapshot.SnapshotArgs;
import com.orbit.service.snapshot.SnapshotResult;
import com.orbit.service.snapshot.SnapshotService;
import com.orbit.service.snapshot.SnapshotStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Read APIs for the snapshot viewer page and one POST entry for non-Slack callers
 * (e.g. a future "Snapshot this view" button in the React UI). Slack flow goes through
 * {@link com.orbit.integration.slack.SlackInteractionRouter} → {@link SnapshotService#request}
 * directly without touching this controller.
 *
 * Ownership: a snapshot's artifacts are visible only to the user who requested it, or
 * to any ADMIN. Token in the URL is intentionally absent — the page already carries the
 * JWT cookie.
 */
@RestController
@RequestMapping("/api/v1/snapshots")
public class SnapshotController {

    private final SnapshotRepository snapshots;
    private final SnapshotService snapshotService;
    private final SnapshotStorageService storage;
    private final AppUserRepository users;

    public SnapshotController(SnapshotRepository snapshots,
                              SnapshotService snapshotService,
                              SnapshotStorageService storage,
                              AppUserRepository users) {
        this.snapshots = snapshots;
        this.snapshotService = snapshotService;
        this.storage = storage;
        this.users = users;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> request(@RequestBody Map<String, Object> body, Authentication auth) {
        AppUser user = users.findByEmail(auth.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();
        try {
            SnapshotArgs args = new SnapshotArgs(
                stringOf(body.get("kind")),
                longOf(body.get("portfolioId")),
                stringOf(body.get("lens")),
                longOf(body.get("projectId"))
            );
            SnapshotResult r = snapshotService.request(user, args);
            return ResponseEntity.ok(Map.of(
                "id",        r.id(),
                "state",     r.state().name(),
                "fromCache", r.fromCache(),
                "dedup",     r.dedup()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> status(@PathVariable Long id, Authentication auth) {
        Optional<Snapshot> opt = snapshots.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Snapshot s = opt.get();
        if (!canRead(s, auth)) return ResponseEntity.status(403).build();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id",          s.getId());
        out.put("state",       s.getState().name());
        out.put("kind",        s.getKind());
        out.put("lens",        s.getLens());
        out.put("portfolioId", s.getPortfolioId());
        out.put("projectId",   s.getProjectId());
        out.put("createdAt",   s.getCreatedAt());
        out.put("completedAt", s.getCompletedAt());
        if (s.getState() == SnapshotState.READY) {
            out.put("downloadPng", "/api/v1/snapshots/" + s.getId() + "/png");
            out.put("downloadPdf", "/api/v1/snapshots/" + s.getId() + "/pdf");
        }
        if (s.getState() == SnapshotState.FAILED) {
            out.put("error", s.getErrorMessage());
        }
        if (s.getState() == SnapshotState.PENDING || s.getState() == SnapshotState.RUNNING) {
            long ageMs = java.time.Duration.between(s.getCreatedAt(), LocalDateTime.now()).toMillis();
            out.put("etaSeconds", Math.max(0, 8 - (int)(ageMs / 1000)));
        }
        return ResponseEntity.ok(out);
    }

    @GetMapping("/{id}/png")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> downloadPng(@PathVariable Long id, Authentication auth) {
        return downloadArtifact(id, auth, true);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> downloadPdf(@PathVariable Long id, Authentication auth) {
        return downloadArtifact(id, auth, false);
    }

    private ResponseEntity<?> downloadArtifact(Long id, Authentication auth, boolean png) {
        Optional<Snapshot> opt = snapshots.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Snapshot s = opt.get();
        if (!canRead(s, auth)) return ResponseEntity.status(403).build();
        if (s.getExpiresAt() != null && s.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(410).body(Map.of("error", "expired"));
        }
        if (s.getState() != SnapshotState.READY) {
            return ResponseEntity.status(409).body(Map.of("error", "not_ready", "state", s.getState().name()));
        }
        String path = png ? s.getPngPath() : s.getPdfPath();
        byte[] bytes = path == null ? null : storage.read(path);
        if (bytes == null) return ResponseEntity.status(500).body(Map.of("error", "artifact_missing"));

        String filename = "orbit-snapshot-" + s.getId() + (png ? ".png" : ".pdf");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
            .contentType(png ? MediaType.IMAGE_PNG : MediaType.APPLICATION_PDF)
            .body(bytes);
    }

    private boolean canRead(Snapshot s, Authentication auth) {
        if (auth == null) return false;
        AppUser u = users.findByEmail(auth.getName()).orElse(null);
        if (u == null) return false;
        if ("ADMIN".equalsIgnoreCase(u.getRole())) return true;
        return s.getUserId() != null && s.getUserId().equals(u.getId());
    }

    private static String stringOf(Object v) { return v == null ? null : v.toString(); }
    private static Long longOf(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }
}
