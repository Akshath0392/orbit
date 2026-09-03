package com.orbit.controller;

import com.orbit.domain.config.FeatureFlag;
import com.orbit.repository.FeatureFlagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
public class FeatureFlagController {

    private final FeatureFlagRepository flags;

    public FeatureFlagController(FeatureFlagRepository flags) {
        this.flags = flags;
    }

    // Effective visibility for the caller. Unknown keys are visible by design, so
    // the frontend only needs the keys that exist here; missing key == ON.
    @GetMapping("/api/v1/feature-flags/effective")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Boolean> effective() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth != null && auth.getName() != null ? auth.getName().toLowerCase() : "";
        boolean admin = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        Map<String, Boolean> out = new LinkedHashMap<>();
        for (FeatureFlag f : flags.findAll()) {
            out.put(f.getFlagKey(), admin || isVisible(f, email));
        }
        return out;
    }

    private static boolean isVisible(FeatureFlag f, String email) {
        return switch (f.getAudience()) {
            case FeatureFlag.AUDIENCE_ALL -> true;
            case FeatureFlag.AUDIENCE_PILOT -> f.getPilotEmails().stream()
                .anyMatch(e -> e != null && e.trim().toLowerCase().equals(email));
            default -> false;   // NONE or unrecognised audience — hold back
        };
    }

    @GetMapping("/api/v1/admin/feature-flags")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return flags.findAll(PageRequest.of(page, size, Sort.by("flagKey")))
            .map(FeatureFlagController::toResponse);
    }

    // Upsert by flagKey so the admin UI doesn't need separate create/update paths.
    @PostMapping("/api/v1/admin/feature-flags")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> upsert(@RequestBody Map<String, Object> body) {
        String key = trimOrNull(body.get("flagKey"));
        if (key == null) return ResponseEntity.badRequest().body(Map.of("error", "flagKey is required"));

        String audience = Optional.ofNullable(trimOrNull(body.get("audience")))
            .map(String::toUpperCase).orElse(FeatureFlag.AUDIENCE_ALL);
        if (!Set.of(FeatureFlag.AUDIENCE_ALL, FeatureFlag.AUDIENCE_PILOT, FeatureFlag.AUDIENCE_NONE).contains(audience)) {
            return ResponseEntity.badRequest().body(Map.of("error", "audience must be ALL, PILOT or NONE"));
        }

        FeatureFlag f = flags.findByFlagKey(key).orElseGet(FeatureFlag::new);
        f.setFlagKey(key);
        if (body.containsKey("description")) f.setDescription(trimOrNull(body.get("description")));
        f.setAudience(audience);
        if (body.containsKey("pilotEmails")) f.setPilotEmails(toEmailList(body.get("pilotEmails")));
        f.setUpdatedBy(currentUsername());
        f.setUpdatedAt(LocalDateTime.now());
        flags.save(f);
        return ResponseEntity.ok(toResponse(f));
    }

    @DeleteMapping("/api/v1/admin/feature-flags/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!flags.existsById(id)) return ResponseEntity.notFound().build();
        flags.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static List<String> toEmailList(Object v) {
        if (!(v instanceof Collection<?> c)) return new ArrayList<>();
        return c.stream()
            .map(e -> e == null ? null : e.toString().trim().toLowerCase())
            .filter(e -> e != null && !e.isEmpty())
            .distinct()
            .toList();
    }

    private static Map<String, Object> toResponse(FeatureFlag f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("flagKey", f.getFlagKey());
        m.put("description", f.getDescription());
        m.put("audience", f.getAudience());
        m.put("pilotEmails", f.getPilotEmails());
        m.put("updatedBy", f.getUpdatedBy());
        m.put("updatedAt", f.getUpdatedAt());
        return m;
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "admin";
    }

    private static String trimOrNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
