package com.orbit.service.snapshot;

import com.orbit.domain.client.AppUser;
import com.orbit.domain.snapshot.Snapshot;
import com.orbit.domain.snapshot.SnapshotState;
import com.orbit.repository.AppUserRepository;
import com.orbit.repository.SnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Executes a single snapshot render. Invoked asynchronously by {@link SnapshotService};
 * the public entry point is {@link #renderAsync(long)} which never throws — outcome is
 * persisted on the {@link Snapshot} row so the viewer page can poll for it.
 */
@Service
public class RadarSnapshotAgent {

    private static final Logger log = LoggerFactory.getLogger(RadarSnapshotAgent.class);

    private final SnapshotRepository snapshots;
    private final AppUserRepository users;
    private final SnapshotJwtService jwt;
    private final SnapshotRendererClient renderer;
    private final SnapshotStorageService storage;
    private final String frontendUrl;
    private final int timeoutMs;

    public RadarSnapshotAgent(SnapshotRepository snapshots,
                              AppUserRepository users,
                              SnapshotJwtService jwt,
                              SnapshotRendererClient renderer,
                              SnapshotStorageService storage,
                              @Value("${orbit.frontend.url:http://localhost:3000}") String frontendUrl,
                              @Value("${snapshot.sidecar.timeout-ms:20000}") int timeoutMs) {
        this.snapshots = snapshots;
        this.users = users;
        this.jwt = jwt;
        this.renderer = renderer;
        this.storage = storage;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.timeoutMs = timeoutMs;
    }

    @Async
    public void renderAsync(long snapshotId) {
        render(snapshotId);
    }

    /** Synchronous variant — used by tests and the agent's own async entry. Never throws. */
    public void render(long snapshotId) {
        Optional<Snapshot> opt = snapshots.findById(snapshotId);
        if (opt.isEmpty()) { log.warn("snapshot {} not found — skipping render", snapshotId); return; }
        Snapshot snap = opt.get();
        AppUser user = users.findById(snap.getUserId()).orElse(null);
        if (user == null) { fail(snap, "user not found"); return; }

        snap.setState(SnapshotState.RUNNING);
        snapshots.save(snap);

        try {
            String token = jwt.mintFor(user);
            String url = buildTargetUrl(snap);
            SnapshotRendererClient.RenderResult out = renderer.render(
                new SnapshotRendererClient.RenderRequest(
                    url, token, 1440, 1024,
                    "[data-snapshot-ready='true']", timeoutMs));
            if (out == null || out.png() == null || out.pdf() == null) {
                fail(snap, "renderer returned empty payload");
                return;
            }
            SnapshotStorageService.Stored stored = storage.save(snap.getId(), out.png(), out.pdf());
            snap.setPngPath(stored.pngPath());
            snap.setPdfPath(stored.pdfPath());
            snap.setState(SnapshotState.READY);
            snap.setCompletedAt(LocalDateTime.now());
            snapshots.save(snap);
            log.info("snapshot {} ready ({} ms)", snapshotId, out.renderMs());
        } catch (RuntimeException e) {
            fail(snap, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private void fail(Snapshot snap, String message) {
        snap.setState(SnapshotState.FAILED);
        snap.setErrorMessage(message);
        snap.setCompletedAt(LocalDateTime.now());
        snapshots.save(snap);
        log.warn("snapshot {} FAILED: {}", snap.getId(), message);
    }

    private String buildTargetUrl(Snapshot s) {
        StringBuilder sb = new StringBuilder(frontendUrl).append("/radar?snapshot=1");
        sb.append("&lens=").append(enc(s.getLens()));
        if (s.getPortfolioId() != null) sb.append("&portfolio=").append(s.getPortfolioId());
        if (s.getProjectId()   != null) sb.append("&project=").append(s.getProjectId());
        return sb.toString();
    }

    private static String enc(String v) {
        return v == null ? "" : URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
