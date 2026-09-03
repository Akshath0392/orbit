package com.orbit.service.hrms;

import com.orbit.connector.hrms.HrmsConnector;
import com.orbit.connector.hrms.HrmsConnectorFactory;
import com.orbit.connector.hrms.HrmsSettingField;
import com.orbit.connector.hrms.HrmsSettings;
import com.orbit.domain.hrms.HrmsConfig;
import com.orbit.domain.hrms.HrmsSyncRun;
import com.orbit.repository.AttendanceRecordRepository;
import com.orbit.repository.HrmsConfigRepository;
import com.orbit.repository.HrmsSyncRunRepository;
import com.orbit.repository.LeaveRecordRepository;
import com.orbit.repository.WfhRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provider-agnostic HRMS orchestration: resolves the configured connector from
 * the single hrms_config row, owns sync-run bookkeeping and the scheduled delta
 * sync, and degrades to a no-op when no provider is configured — capacity
 * features simply see whatever HR data already exists.
 */
@Service
public class HrmsSyncService {

    private static final Logger log = LoggerFactory.getLogger(HrmsSyncService.class);

    private final HrmsConfigRepository       configs;
    private final HrmsSyncRunRepository      syncRuns;
    private final HrmsConnectorFactory       connectors;
    private final LeaveRecordRepository      leaves;
    private final WfhRecordRepository        wfhRecords;
    private final AttendanceRecordRepository attendance;

    public HrmsSyncService(HrmsConfigRepository configs,
                           HrmsSyncRunRepository syncRuns,
                           HrmsConnectorFactory connectors,
                           LeaveRecordRepository leaves,
                           WfhRecordRepository wfhRecords,
                           AttendanceRecordRepository attendance) {
        this.configs = configs; this.syncRuns = syncRuns; this.connectors = connectors;
        this.leaves = leaves; this.wfhRecords = wfhRecords; this.attendance = attendance;
    }

    // ── Config resolution ────────────────────────────────────────────────────

    private Optional<HrmsConfig> config() { return configs.findFirstByOrderByIdAsc(); }

    public Optional<HrmsConnector> activeConnector() {
        return config().map(HrmsConfig::getProviderKey).flatMap(connectors::byKey);
    }

    public Optional<String> activeProviderName() {
        return activeConnector().map(HrmsConnector::displayName);
    }

    private HrmsSettings settings() {
        return config().map(c -> new HrmsSettings(
                c.getSettings() != null ? c.getSettings() : Map.of()))
            .orElse(HrmsSettings.empty());
    }

    private boolean isEnabled() {
        return config().map(c -> Boolean.TRUE.equals(c.getEnabled())).orElse(false);
    }

    public String webhookSecret() { return settings().string("webhookSecret"); }

    // ── Provider catalogue (drives the FE provider dropdown + settings form) ─

    public List<Map<String, Object>> providers() {
        return connectors.all().stream().map(c -> Map.<String, Object>of(
            "key",    c.providerKey(),
            "name",   c.displayName(),
            "fields", c.settingsDescriptor()
        )).toList();
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

    @Scheduled(cron = "${orbit.hrms.sync-cron:0 0 */4 * * *}")
    public void scheduledDeltaSync() {
        if (!isEnabled() || activeConnector().isEmpty()) return;
        log.info("HRMS scheduled delta sync starting");
        sync("DELTA");
    }

    public Map<String, Object> triggerSync(String type) { return sync(type); }

    private Map<String, Object> sync(String type) {
        HrmsSyncRun run = new HrmsSyncRun();
        run.setSyncType(type); run.setStatus("IN_PROGRESS"); syncRuns.save(run);
        try {
            Optional<HrmsConnector> connector = activeConnector();
            HrmsSettings s = settings();
            int pulled;
            if (isEnabled() && connector.isPresent() && connector.get().isConfigured(s)) {
                pulled = connector.get().sync(s, type);
            } else {
                pulled = (int) (leaves.count() + wfhRecords.count() + attendance.count());
                log.info("HRMS not configured — {} records already in DB", pulled);
            }
            run.setStatus("SUCCESS"); run.setRecordsPulled(pulled);
            run.setCompletedAt(LocalDateTime.now()); syncRuns.save(run);
            return Map.of("status", "SUCCESS", "recordsPulled", pulled, "syncedAt", run.getCompletedAt().toString());
        } catch (Exception e) {
            log.error("HRMS sync failed: {}", e.getMessage(), e);
            run.setStatus("FAILED"); run.setErrorMessage(e.getMessage());
            run.setCompletedAt(LocalDateTime.now()); syncRuns.save(run);
            throw new RuntimeException("HRMS sync failed: " + e.getMessage());
        }
    }

    public Map<String, Object> testConnection() {
        Optional<HrmsConnector> connector = activeConnector();
        if (connector.isEmpty()) return Map.of("ok", false, "error", "No HRMS provider configured");
        return connector.get().testConnection(settings());
    }

    public void processWebhookEvent(Map<String, Object> payload) {
        activeConnector().ifPresent(c -> c.processWebhookEvent(settings(), payload));
    }

    // ── Status + config views ────────────────────────────────────────────────

    public Map<String, Object> getConnectionStatus() {
        Optional<HrmsConnector> connector = activeConnector();
        HrmsSettings s = settings();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider",     connector.map(HrmsConnector::providerKey).orElse(null));
        m.put("providerName", connector.map(HrmsConnector::displayName).orElse(null));
        m.put("enabled",      isEnabled());
        m.put("configured",   connector.map(c -> c.isConfigured(s)).orElse(false));
        syncRuns.findTop20ByOrderByStartedAtDesc().stream().findFirst().ifPresent(r -> {
            m.put("lastSyncStatus", r.getStatus());
            m.put("lastSyncAt", r.getCompletedAt() != null ? r.getCompletedAt().toString() : null);
        });
        return m;
    }

    /** Settings echo for the form — secret values are replaced by a set/unset flag. */
    public Map<String, Object> getConfigView() {
        Optional<HrmsConfig> cfg = config();
        Optional<HrmsConnector> connector = activeConnector();
        HrmsSettings s = settings();

        Map<String, Object> visible = new LinkedHashMap<>();
        Map<String, Object> secretsSet = new LinkedHashMap<>();
        connector.ifPresent(c -> {
            for (HrmsSettingField f : c.settingsDescriptor()) {
                if (f.secret()) secretsSet.put(f.key(), s.has(f.key()));
                else            visible.put(f.key(), s.string(f.key(), ""));
            }
        });

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider",     cfg.map(HrmsConfig::getProviderKey).orElse(null));
        m.put("providerName", connector.map(HrmsConnector::displayName).orElse(null));
        m.put("enabled",      isEnabled());
        m.put("settings",     visible);
        m.put("secretsSet",   secretsSet);
        return m;
    }

    /**
     * Saves provider + settings. Secret fields left blank (or still masked)
     * keep their stored value; switching provider discards the old settings.
     */
    @SuppressWarnings("unchecked")
    public void saveConfig(Map<String, Object> body, String updatedBy) {
        HrmsConfig cfg = configs.findFirstByOrderByIdAsc().orElse(new HrmsConfig());

        String provider = body.get("provider") != null ? body.get("provider").toString().strip() : null;
        if (provider != null && !provider.isEmpty() && connectors.byKey(provider).isEmpty()) {
            throw new IllegalArgumentException("Unknown HRMS provider: " + provider);
        }
        boolean providerChanged = provider != null && !provider.equals(cfg.getProviderKey());
        if (provider != null) cfg.setProviderKey(provider.isEmpty() ? null : provider);

        Map<String, Object> incoming = body.get("settings") instanceof Map<?, ?> sm
            ? (Map<String, Object>) sm : Map.of();
        Map<String, Object> merged = providerChanged
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(cfg.getSettings() != null ? cfg.getSettings() : Map.of());

        connectors.byKey(cfg.getProviderKey()).ifPresent(c -> {
            for (HrmsSettingField f : c.settingsDescriptor()) {
                Object raw = incoming.get(f.key());
                String v = raw != null ? raw.toString().strip() : null;
                if (f.secret()) {
                    // Blank or masked → keep existing secret.
                    if (v != null && !v.isEmpty() && !v.startsWith("•")) merged.put(f.key(), v);
                } else if (raw != null) {
                    if (v.isEmpty()) merged.remove(f.key());
                    else {
                        if ("url".equals(f.type())) {
                            com.orbit.integration.SafeUrl.validatePublicHttps(v);  // anti-SSRF (M6)
                        }
                        merged.put(f.key(), v);
                    }
                }
            }
        });

        cfg.setSettings(merged);
        if (body.containsKey("enabled")) cfg.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        cfg.setUpdatedAt(LocalDateTime.now());
        cfg.setUpdatedBy(updatedBy);
        configs.save(cfg);
    }
}
