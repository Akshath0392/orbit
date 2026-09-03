package com.orbit.service.snapshot;

/**
 * Abstraction over the headless-browser renderer. Phase A uses {@link MockSnapshotRendererClient}
 * (returns a constant 1x1 PNG / minimal PDF so the rest of the stack can be wired and tested
 * before the Playwright sidecar exists). Phase B swaps in an HTTP client against the sidecar.
 */
public interface SnapshotRendererClient {

    /** Renders the page at {@code targetUrl} and returns PNG + PDF bytes. */
    RenderResult render(RenderRequest req);

    record RenderRequest(
        String targetUrl,
        String jwt,
        int    viewportWidth,
        int    viewportHeight,
        String readySelector,
        int    timeoutMs
    ) {}

    record RenderResult(byte[] png, byte[] pdf, long renderMs) {}
}
