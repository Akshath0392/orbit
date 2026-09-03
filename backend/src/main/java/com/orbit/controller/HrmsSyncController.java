package com.orbit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.repository.*;
import com.orbit.service.hrms.HrmsSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/hrms")
public class HrmsSyncController {

    private static final Logger log = LoggerFactory.getLogger(HrmsSyncController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HrmsSyncService          hrms;
    private final HrmsSyncRunRepository    syncRuns;
    private final LeaveRecordRepository    leaves;
    private final LeaveBalanceRepository   balances;
    private final AttendanceRecordRepository attendance;
    private final AppUserRepository        users;
    private final WfhRecordRepository      wfhRecords;

    public HrmsSyncController(HrmsSyncService hrms,
                              HrmsSyncRunRepository syncRuns,
                              LeaveRecordRepository leaves,
                              LeaveBalanceRepository balances,
                              AttendanceRecordRepository attendance,
                              AppUserRepository users,
                              WfhRecordRepository wfhRecords) {
        this.hrms = hrms; this.syncRuns = syncRuns;
        this.leaves = leaves; this.balances = balances;
        this.attendance = attendance; this.users = users; this.wfhRecords = wfhRecords;
    }

    // ── Registered providers + settings descriptors ───────────────────────────
    @GetMapping("/providers")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public List<Map<String, Object>> providers() { return hrms.providers(); }

    // ── Connection status ─────────────────────────────────────────────────────
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String, Object> status() { return hrms.getConnectionStatus(); }

    // ── Config (read + save) ──────────────────────────────────────────────────
    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String, Object> getConfig() { return hrms.getConfigView(); }

    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> saveConfig(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        String by = req.getUserPrincipal() != null ? req.getUserPrincipal().getName() : "unknown";
        try {
            hrms.saveConfig(body, by);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(hrms.getConfigView());
    }

    // ── Connection test ───────────────────────────────────────────────────────
    @PostMapping("/test")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String, Object> testConnection() { return hrms.testConnection(); }

    // ── Sync runs ─────────────────────────────────────────────────────────────
    @GetMapping("/runs")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public List<Map<String, Object>> syncRuns() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, HH:mm");
        return syncRuns.findTop20ByOrderByStartedAtDesc().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",            r.getId());
            m.put("type",          r.getSyncType());
            m.put("status",        r.getStatus());
            m.put("recordsPulled", r.getRecordsPulled());
            m.put("startedAt",     r.getStartedAt()   != null ? r.getStartedAt().format(fmt) : "");
            m.put("completedAt",   r.getCompletedAt() != null ? r.getCompletedAt().format(fmt) : "—");
            m.put("errorMessage",  r.getErrorMessage());
            return m;
        }).collect(Collectors.toList());
    }

    // ── Manual sync trigger ───────────────────────────────────────────────────
    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> triggerSync(@RequestParam(defaultValue = "DELTA") String type) {
        try { return ResponseEntity.ok(hrms.triggerSync(type)); }
        catch (Exception e) { return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage())); }
    }

    // ── Employee mapping ──────────────────────────────────────────────────────
    @GetMapping("/employees")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public List<Map<String, Object>> employeeMapping() {
        return users.findAll().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",        u.getId());
            m.put("name",      u.getName());
            m.put("email",     u.getEmail());
            m.put("role",      u.getRole());
            m.put("av",        u.getInitials());
            m.put("color",     u.getAvatarColor());
            m.put("hrmsEmpId", u.getDarwinEmpId());
            m.put("mapped",    u.getDarwinEmpId() != null && !u.getDarwinEmpId().isBlank());
            return m;
        }).collect(Collectors.toList());
    }

    @PatchMapping("/employees/{id}/emp-id")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> setEmpId(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return users.findById(id).map(u -> {
            String empId = (String) body.get("hrmsEmpId");
            u.setDarwinEmpId(empId == null || empId.isBlank() ? null : empId.trim());
            users.save(u);
            return ResponseEntity.ok(Map.of("ok", true, "hrmsEmpId",
                u.getDarwinEmpId() != null ? u.getDarwinEmpId() : ""));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Leave records ─────────────────────────────────────────────────────────
    @GetMapping("/leaves")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> leaveRecords(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        var records = (from != null && to != null)
            ? leaves.findByStartDateBetweenOrderByStartDateAsc(LocalDate.parse(from), LocalDate.parse(to))
            : status != null
                ? leaves.findByStatusInOrderByStartDateAsc(List.of(status.split(",")))
                : leaves.findUpcoming(LocalDate.now());

        DateTimeFormatter f = DateTimeFormatter.ofPattern("MMM d");
        DateTimeFormatter fY = DateTimeFormatter.ofPattern("MMM d, yyyy");
        return records.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",          l.getId());
            m.put("hrmsEmpId",   l.getDarwinEmpId());
            m.put("hrmsLeaveId", l.getDarwinLeaveId());
            m.put("name",        l.getUser() != null ? l.getUser().getName() : "Unknown");
            m.put("av",          l.getUser() != null ? l.getUser().getInitials() : "?");
            m.put("color",       l.getUser() != null ? l.getUser().getAvatarColor() : "#6366F1");
            m.put("leaveType",   l.getLeaveType());
            m.put("from",        l.getStartDate() != null ? l.getStartDate().format(f) : "");
            m.put("to",          l.getEndDate()   != null ? l.getEndDate().format(fY) : "");
            m.put("days",        l.getWorkingDays());
            m.put("status",      l.getStatus());
            m.put("syncedAt",    l.getSyncedAt() != null ? l.getSyncedAt().format(DateTimeFormatter.ofPattern("MMM d, HH:mm")) : "");
            return m;
        }).collect(Collectors.toList());
    }

    // ── WFH records ───────────────────────────────────────────────────────────
    @GetMapping("/wfh")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> wfhRecords(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        var records = (from != null && to != null)
            ? wfhRecords.findByWfhDateBetweenOrderByWfhDateAsc(LocalDate.parse(from), LocalDate.parse(to))
            : status != null
                ? wfhRecords.findByStatusInOrderByWfhDateAsc(List.of(status.split(",")))
                : wfhRecords.findUpcoming(LocalDate.now());

        DateTimeFormatter fY = DateTimeFormatter.ofPattern("MMM d, yyyy");
        return records.stream().map(w -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",        w.getId());
            m.put("hrmsEmpId", w.getDarwinEmpId());
            m.put("hrmsWfhId", w.getDarwinWfhId());
            m.put("name",      w.getUser() != null ? w.getUser().getName() : "Unknown");
            m.put("av",        w.getUser() != null ? w.getUser().getInitials() : "?");
            m.put("color",     w.getUser() != null ? w.getUser().getAvatarColor() : "#6366F1");
            m.put("wfhDate",   w.getWfhDate() != null ? w.getWfhDate().format(fY) : "");
            m.put("wfhType",   w.getWfhType());
            m.put("status",    w.getStatus());
            m.put("reason",    w.getReason());
            m.put("syncedAt",  w.getSyncedAt() != null ? w.getSyncedAt().format(DateTimeFormatter.ofPattern("MMM d, HH:mm")) : "");
            return m;
        }).collect(Collectors.toList());
    }

    // ── Leave balances ────────────────────────────────────────────────────────
    @GetMapping("/balances")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> leaveBalances(
            @RequestParam(required = false) Long userId) {
        var records = userId != null
            ? balances.findByUserIdOrderByLeaveTypeAsc(userId)
            : balances.findAll();
        return records.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",            b.getId());
            m.put("hrmsEmpId",     b.getDarwinEmpId());
            m.put("name",          b.getUser() != null ? b.getUser().getName() : "Unknown");
            m.put("av",            b.getUser() != null ? b.getUser().getInitials() : "?");
            m.put("color",         b.getUser() != null ? b.getUser().getAvatarColor() : "#6366F1");
            m.put("leaveType",     b.getLeaveType());
            m.put("totalDays",     b.getTotalDays());
            m.put("takenDays",     b.getTakenDays());
            m.put("pendingDays",   b.getPendingDays());
            m.put("remainingDays", b.getRemainingDays());
            m.put("syncedAt",      b.getSyncedAt() != null ? b.getSyncedAt().format(DateTimeFormatter.ofPattern("MMM d, HH:mm")) : "");
            return m;
        }).collect(Collectors.toList());
    }

    // ── Attendance ────────────────────────────────────────────────────────────
    @GetMapping("/attendance")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> attendanceRecords(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long userId) {
        DateTimeFormatter fY = DateTimeFormatter.ofPattern("MMM d, yyyy");
        var records = userId != null
            ? attendance.findByUserIdOrderByAttendanceDateDesc(userId)
            : (from != null && to != null)
                ? attendance.findByAttendanceDateBetweenOrderByAttendanceDateAsc(LocalDate.parse(from), LocalDate.parse(to))
                : attendance.findByAttendanceDateBetweenOrderByAttendanceDateAsc(LocalDate.now().minusDays(30), LocalDate.now());

        return records.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",           a.getId());
            m.put("hrmsEmpId",    a.getDarwinEmpId());
            m.put("name",         a.getUser() != null ? a.getUser().getName() : "Unknown");
            m.put("av",           a.getUser() != null ? a.getUser().getInitials() : "?");
            m.put("color",        a.getUser() != null ? a.getUser().getAvatarColor() : "#6366F1");
            m.put("date",         a.getAttendanceDate() != null ? a.getAttendanceDate().format(fY) : "");
            m.put("checkIn",      a.getCheckIn()  != null ? a.getCheckIn().toString() : "—");
            m.put("checkOut",     a.getCheckOut() != null ? a.getCheckOut().toString() : "—");
            m.put("workingHours", a.getWorkingHours());
            m.put("status",       a.getStatus());
            return m;
        }).collect(Collectors.toList());
    }

    // ── Webhook receiver ──────────────────────────────────────────────────────
    // Public endpoint: authenticated ONLY by the HMAC-SHA256 signature over the raw
    // body, using the provider's signature header. Fails closed — no configured
    // provider, missing secret, or a bad/absent signature is rejected.
    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestBody(required = false) String rawBody, HttpServletRequest req) {
        var connector = hrms.activeConnector();
        if (connector.isEmpty()) {
            return ResponseEntity.status(503).body(Map.of("error", "no HRMS provider configured"));
        }
        String secret = hrms.webhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("HRMS webhook rejected: no webhookSecret configured — refusing to process unsigned events");
            return ResponseEntity.status(503).body(Map.of("error", "webhook secret not configured"));
        }
        String sig = req.getHeader(connector.get().webhookSignatureHeader());
        if (rawBody == null) rawBody = "";
        if (!isSignatureValid(secret, sig, rawBody)) {
            log.warn("HRMS webhook rejected: invalid or missing {}", connector.get().webhookSignatureHeader());
            return ResponseEntity.status(401).body(Map.of("error", "invalid signature"));
        }

        Map<String, Object> payload;
        try {
            //noinspection unchecked
            payload = MAPPER.readValue(rawBody, Map.class);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid JSON"));
        }
        try {
            hrms.processWebhookEvent(payload);
            return ResponseEntity.ok(Map.of("status", "accepted"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Verifies the HMAC-SHA256 of the raw body against the provider's signature
     * header. Accepts the signature as raw hex or with a {@code sha256=} prefix.
     * Constant-time.
     */
    private boolean isSignatureValid(String secret, String header, String body) {
        if (header == null || header.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            String provided = header.trim();
            if (provided.regionMatches(true, 0, "sha256=", 0, 7)) provided = provided.substring(7);
            return constantTimeEquals(computedHex, provided.toLowerCase());
        } catch (Exception e) {
            log.error("HRMS webhook HMAC computation failed — {}", e.getMessage());
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
