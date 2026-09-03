package com.orbit.service.snapshot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Phase-A placeholder renderer. Returns a minimal valid 1x1 PNG and a near-empty PDF so the
 * service / controller / agent code paths can be exercised without the Playwright sidecar.
 * Wired by default; the real HTTP client (Phase B) will register under the same interface
 * with {@code matchIfMissing=false} and become the default once the sidecar URL is set.
 */
@Component
@ConditionalOnProperty(name = "snapshot.renderer", havingValue = "mock", matchIfMissing = true)
public class MockSnapshotRendererClient implements SnapshotRendererClient {

    // 1x1 transparent PNG (smallest legal PNG file)
    private static final byte[] PNG_1X1 = {
        (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte)0xC4,
        (byte)0x89, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
        0x54, 0x78, (byte)0x9C, 0x62, 0x00, 0x01, 0x00, 0x00,
        0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte)0xB4, 0x00,
        0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte)0xAE,
        0x42, 0x60, (byte)0x82
    };

    // Minimal one-page PDF
    private static final byte[] PDF_STUB =
        ("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
       + "2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n"
       + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\n"
       + "xref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000054 00000 n\n0000000099 00000 n\n"
       + "trailer<</Size 4/Root 1 0 R>>\nstartxref\n148\n%%EOF")
        .getBytes();

    @Override
    public RenderResult render(RenderRequest req) {
        return new RenderResult(PNG_1X1, PDF_STUB, 0L);
    }
}
