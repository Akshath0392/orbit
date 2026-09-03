package com.orbit.service.snapshot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Inputs to a snapshot request. Lens is required; portfolio + project are optional
 * filters that scope the rendered page. Kind defaults to "RADAR" (the only target
 * in v1; future targets like COCKPIT add their own constant).
 */
public record SnapshotArgs(
    String  kind,
    Long    portfolioId,
    String  lens,
    Long    projectId
) {
    public static final String KIND_RADAR = "RADAR";

    public SnapshotArgs {
        if (kind == null || kind.isBlank()) kind = KIND_RADAR;
        if (lens == null || lens.isBlank()) {
            throw new IllegalArgumentException("snapshot lens is required");
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        if (portfolioId != null) m.put("portfolioId", portfolioId);
        m.put("lens", lens);
        if (projectId   != null) m.put("projectId",   projectId);
        return m;
    }
}
