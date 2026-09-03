package com.orbit.service.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Phase-B renderer: POSTs to the Playwright sidecar service at
 * {@code snapshot.sidecar.url} and returns the decoded PNG + PDF bytes.
 *
 * <p>Active when {@code snapshot.renderer=http}. {@link MockSnapshotRendererClient}
 * stays the default for tests and local dev without the sidecar.</p>
 */
@Component
@ConditionalOnProperty(name = "snapshot.renderer", havingValue = "http")
public class HttpSnapshotRendererClient implements SnapshotRendererClient {

    private static final Logger log = LoggerFactory.getLogger(HttpSnapshotRendererClient.class);

    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String sidecarUrl;
    private final int timeoutMs;
    private final String sharedSecret;

    public HttpSnapshotRendererClient(
            @Value("${snapshot.sidecar.url:http://snapshot-sidecar:3001}") String sidecarUrl,
            @Value("${snapshot.sidecar.timeout-ms:20000}") int timeoutMs,
            @Value("${snapshot.sidecar.shared-secret:}") String sharedSecret) {
        this.sidecarUrl = sidecarUrl.replaceAll("/+$", "");
        this.timeoutMs = timeoutMs;
        this.sharedSecret = sharedSecret;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    @Override
    public RenderResult render(RenderRequest req) {
        try {
            Map<String, Object> body = Map.of(
                "targetUrl", req.targetUrl(),
                "jwt", req.jwt(),
                "viewport", Map.of("w", req.viewportWidth(), "h", req.viewportHeight()),
                "formats", new String[]{"png", "pdf"},
                "waitForSelector", req.readySelector(),
                "timeoutMs", req.timeoutMs()
            );
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(sidecarUrl + "/render"))
                .timeout(Duration.ofMillis(timeoutMs + 5000L))
                .header("Content-Type", "application/json");
            if (sharedSecret != null && !sharedSecret.isBlank()) {
                builder.header("X-Snapshot-Secret", sharedSecret);
            }
            HttpRequest http = builder
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
            long t0 = System.currentTimeMillis();
            HttpResponse<String> resp = this.http.send(http, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                // On failure the sidecar embeds a `diagnostics` object (consoleMsgs, pageErrors,
                // failedRequests, debug screenshot). Log it so the operator can see *why* the
                // page never reached data-snapshot-ready, instead of just the timeout message.
                try {
                    JsonNode errBody = mapper.readTree(resp.body());
                    JsonNode diag = errBody.path("diagnostics");
                    if (!diag.isMissingNode()) {
                        log.warn("sidecar diagnostics url={} consoleMsgs={} pageErrors={} failedRequests={}",
                            diag.path("url").asText(),
                            diag.path("consoleMsgs"),
                            diag.path("pageErrors"),
                            diag.path("failedRequests"));
                    }
                } catch (Exception ignored) { /* best-effort logging only */ }
                throw new IllegalStateException("sidecar HTTP " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode json = mapper.readTree(resp.body());
            byte[] png = json.hasNonNull("png") ? Base64.getDecoder().decode(json.get("png").asText()) : null;
            byte[] pdf = json.hasNonNull("pdf") ? Base64.getDecoder().decode(json.get("pdf").asText()) : null;
            long renderMs = json.hasNonNull("renderMs") ? json.get("renderMs").asLong() : (System.currentTimeMillis() - t0);
            log.info("sidecar render ok pngBytes={} pdfBytes={} renderMs={}",
                png != null ? png.length : 0, pdf != null ? pdf.length : 0, renderMs);
            return new RenderResult(png, pdf, renderMs);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("sidecar call failed: " + e.getMessage(), e);
        }
    }
}
