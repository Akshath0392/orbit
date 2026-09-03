package com.orbit.service.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies HttpSnapshotRendererClient against a tiny in-process HTTP server stub
 * — no Playwright dependency at unit-test time.
 */
class HttpSnapshotRendererClientTest {

    HttpServer server;
    ObjectMapper mapper = new ObjectMapper();
    AtomicReference<String> lastBody = new AtomicReference<>();
    int responseStatus = 200;
    Map<String, Object> responseJson;

    @BeforeEach
    void start() throws IOException {
        responseJson = new HashMap<>();
        responseJson.put("png", Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
        responseJson.put("pdf", Base64.getEncoder().encodeToString(new byte[]{4, 5}));
        responseJson.put("renderMs", 42L);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/render", ex -> {
            byte[] in = ex.getRequestBody().readAllBytes();
            lastBody.set(new String(in, StandardCharsets.UTF_8));
            byte[] out = mapper.writeValueAsBytes(responseJson);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(responseStatus, out.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    private HttpSnapshotRendererClient newClient() {
        return new HttpSnapshotRendererClient(
            "http://127.0.0.1:" + server.getAddress().getPort(), 5000, "");
    }

    @Test
    void success_returns_decoded_payload() {
        var result = newClient().render(new SnapshotRendererClient.RenderRequest(
            "http://example/radar?snapshot=1", "jwt-x", 1440, 900,
            "[data-snapshot-ready=\"true\"]", 5000));
        assertThat(result.png()).containsExactly(1, 2, 3);
        assertThat(result.pdf()).containsExactly(4, 5);
        assertThat(result.renderMs()).isEqualTo(42L);
        assertThat(lastBody.get()).contains("\"targetUrl\":\"http://example/radar?snapshot=1\"");
        assertThat(lastBody.get()).contains("\"jwt\":\"jwt-x\"");
    }

    @Test
    void non_200_response_is_wrapped_as_illegal_state() {
        responseStatus = 500;
        responseJson = Map.of("error", "boom");
        assertThatThrownBy(() -> newClient().render(new SnapshotRendererClient.RenderRequest(
                "http://example/radar?snapshot=1", "jwt-x", 1440, 900, "x", 1000)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("sidecar HTTP 500");
    }

    @Test
    void missing_pdf_field_returns_null_pdf() {
        responseJson = Map.of(
            "png", Base64.getEncoder().encodeToString(new byte[]{9}),
            "renderMs", 7L);
        var result = newClient().render(new SnapshotRendererClient.RenderRequest(
            "http://example", "jwt", 1440, 900, "x", 1000));
        assertThat(result.png()).containsExactly(9);
        assertThat(result.pdf()).isNull();
    }
}
