package com.orbit.connector.hrms.darwinbox;

import com.orbit.connector.hrms.HrmsConnector;
import com.orbit.connector.hrms.HrmsSettingField;
import com.orbit.connector.hrms.HrmsSettings;
import com.orbit.domain.darwin.AttendanceRecord;
import com.orbit.domain.darwin.LeaveBalance;
import com.orbit.domain.darwin.LeaveRecord;
import com.orbit.domain.darwin.WfhRecord;
import com.orbit.repository.AppUserRepository;
import com.orbit.repository.AttendanceRecordRepository;
import com.orbit.repository.LeaveBalanceRepository;
import com.orbit.repository.LeaveRecordRepository;
import com.orbit.repository.WfhRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Darwinbox HRMS provider — the reference {@link HrmsConnector} implementation.
 *
 * Auth types (settings key {@code authType}):
 *   API_KEY  — x-api-key header only (most common)
 *   BEARER   — Authorization: Bearer {apiKey} header
 *   HMAC     — future: HMAC-SHA256 request signing (not yet implemented — requires secret)
 *
 * Darwinbox API response envelope:
 *   { "status": true, "data": [...], "message": "Success" }
 *
 * Darwinbox uses snake_case field names; this connector maps them to Java conventions.
 */
@Service
public class DarwinboxHrmsConnector implements HrmsConnector {

    private static final Logger log = LoggerFactory.getLogger(DarwinboxHrmsConnector.class);
    private static final int PAGE_SIZE = 50;

    private final LeaveRecordRepository      leaves;
    private final LeaveBalanceRepository     balances;
    private final AttendanceRecordRepository attendance;
    private final AppUserRepository          users;
    private final WfhRecordRepository        wfhRecords;
    private final RestTemplate               http = com.orbit.integration.OutboundHttp.restTemplate();

    public DarwinboxHrmsConnector(LeaveRecordRepository leaves,
                                  LeaveBalanceRepository balances,
                                  AttendanceRecordRepository attendance,
                                  AppUserRepository users,
                                  WfhRecordRepository wfhRecords) {
        this.leaves = leaves; this.balances = balances;
        this.attendance = attendance; this.users = users; this.wfhRecords = wfhRecords;
    }

    @Override public String providerKey() { return "darwinbox"; }
    @Override public String displayName() { return "Darwinbox"; }
    @Override public String webhookSignatureHeader() { return "X-Darwin-Signature"; }

    @Override
    public List<HrmsSettingField> settingsDescriptor() {
        return List.of(
            HrmsSettingField.url("baseUrl", "Tenant URL", true, "https://yourtenant.darwinbox.in"),
            HrmsSettingField.text("companyId", "Company ID", true, "Your Darwinbox company ID"),
            HrmsSettingField.secret("apiKey", "API key", true, "Enter API key"),
            HrmsSettingField.select("authType", "Auth type", List.of("API_KEY", "BEARER", "HMAC")),
            HrmsSettingField.number("syncDaysAhead", "Sync days ahead", "90"),
            HrmsSettingField.secret("webhookSecret", "Webhook secret", false, "For X-Darwin-Signature validation")
        );
    }

    @Override
    public boolean isConfigured(HrmsSettings s) {
        return s.has("baseUrl") && s.has("apiKey") && s.has("companyId");
    }

    @Override
    public Map<String, Object> testConnection(HrmsSettings s) {
        if (!isConfigured(s)) {
            return Map.of("ok", false, "error", "Tenant URL, company ID and API key are required");
        }
        try {
            Map<?, ?> resp = http.exchange(
                baseUrl(s) + "/apiv2/employees?page=1&per_page=1", HttpMethod.GET,
                new HttpEntity<>(buildHeaders(s)), Map.class).getBody();
            if (resp == null) return Map.of("ok", false, "error", "Empty response from Darwinbox");
            return Map.of("ok", true, "message", "Connected to " + baseUrl(s));
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    // ── Sync orchestration ───────────────────────────────────────────────────

    @Override
    public int sync(HrmsSettings s, String syncType) {
        LocalDate from = "FULL".equals(syncType) ? LocalDate.now().minusMonths(3) : LocalDate.now().minusDays(1);
        LocalDate to   = LocalDate.now().plusDays(s.integer("syncDaysAhead", 90));
        int count = 0;

        count += syncEmployeeDirectory(s);

        var mappedUsers = users.findAll().stream()
            .filter(u -> u.getDarwinEmpId() != null && !u.getDarwinEmpId().isBlank())
            .collect(Collectors.toList());

        for (var user : mappedUsers) {
            try { count += upsertLeaves(s, user.getDarwinEmpId(), from, to, user); }
            catch (Exception e) { log.warn("Leave sync failed for {}: {}", user.getDarwinEmpId(), e.getMessage()); }

            try { count += upsertWfh(s, user.getDarwinEmpId(), from, to, user); }
            catch (Exception e) { log.warn("WFH sync failed for {}: {}", user.getDarwinEmpId(), e.getMessage()); }

            try { count += upsertLeaveBalances(s, user.getDarwinEmpId(), user); }
            catch (Exception e) { log.warn("Balance sync failed for {}: {}", user.getDarwinEmpId(), e.getMessage()); }

            try { count += upsertAttendance(s, user.getDarwinEmpId(), from, to, user); }
            catch (Exception e) { log.warn("Attendance sync failed for {}: {}", user.getDarwinEmpId(), e.getMessage()); }
        }
        return count;
    }

    // ── Employee directory ───────────────────────────────────────────────────

    /**
     * GET /apiv2/employees  (paginated)
     * Auto-maps Orbit users to Darwinbox employees by email match.
     */
    private int syncEmployeeDirectory(HrmsSettings s) {
        int page = 1, mapped = 0;
        while (true) {
            List<Map<String, Object>> rows = apiGet(s, "/apiv2/employees",
                Map.of("page", String.valueOf(page), "per_page", String.valueOf(PAGE_SIZE)));
            if (rows.isEmpty()) break;

            for (Map<String, Object> raw : rows) {
                String empId = str(raw, "employee_id");
                String email = str(raw, "email");
                if (empId == null || email == null) continue;

                users.findByEmail(email).ifPresent(u -> {
                    if (u.getDarwinEmpId() == null || u.getDarwinEmpId().isBlank()) {
                        u.setDarwinEmpId(empId);
                        users.save(u);
                        log.info("Auto-mapped {} → darwin_emp_id={}", email, empId);
                    }
                });
                mapped++;
            }
            if (rows.size() < PAGE_SIZE) break;
            page++;
        }
        return mapped;
    }

    // ── Leave records ────────────────────────────────────────────────────────

    private int upsertLeaves(HrmsSettings s, String empId, LocalDate from, LocalDate to,
                              com.orbit.domain.client.AppUser user) {
        int count = 0;
        Map<String, Object> body = Map.of(
            "company_id",   s.string("companyId", ""),
            "employee_id",  empId,
            "from_date",    from.toString(),
            "to_date",      to.toString()
        );
        List<Map<String, Object>> rows = apiPost(s, "/apiv2/employees/leavedetails", body);
        for (Map<String, Object> raw : rows) {
            String id = str(raw, "leave_id");
            if (id == null) continue;
            LeaveRecord lr = leaves.findByDarwinLeaveId(id).orElse(new LeaveRecord());
            lr.setUser(user);
            lr.setDarwinEmpId(empId);
            lr.setDarwinLeaveId(id);
            lr.setLeaveType(str(raw, "leave_type"));
            lr.setStartDate(parseDate(str(raw, "from_date")));
            lr.setEndDate(parseDate(str(raw, "to_date")));
            lr.setWorkingDays(toInt(raw.getOrDefault("no_of_days", raw.getOrDefault("working_days", 1))));
            lr.setStatus(normaliseStatus(str(raw, "status")));
            lr.setRemarks(str(raw, "remarks"));
            lr.setSyncedAt(LocalDateTime.now());
            leaves.save(lr); count++;
        }
        return count;
    }

    // ── WFH records ──────────────────────────────────────────────────────────

    private int upsertWfh(HrmsSettings s, String empId, LocalDate from, LocalDate to,
                           com.orbit.domain.client.AppUser user) {
        int count = 0;
        Map<String, Object> body = Map.of(
            "company_id",   s.string("companyId", ""),
            "employee_id",  empId,
            "from_date",    from.toString(),
            "to_date",      to.toString()
        );
        List<Map<String, Object>> rows = apiPost(s, "/apiv2/employees/wfhdetails", body);
        for (Map<String, Object> raw : rows) {
            String id = str(raw, "wfh_id");
            if (id == null) continue;
            WfhRecord wr = wfhRecords.findByDarwinWfhId(id).orElse(new WfhRecord());
            wr.setUser(user);
            wr.setDarwinEmpId(empId);
            wr.setDarwinWfhId(id);
            wr.setWfhDate(parseDate(str(raw, "wfh_date")));
            wr.setWfhType(mapWfhType(str(raw, "wfh_type")));
            wr.setStatus(normaliseStatus(str(raw, "status")));
            wr.setReason(str(raw, "reason"));
            wr.setSyncedAt(LocalDateTime.now());
            wfhRecords.save(wr); count++;
        }
        return count;
    }

    // ── Leave balances ───────────────────────────────────────────────────────

    private int upsertLeaveBalances(HrmsSettings s, String empId, com.orbit.domain.client.AppUser user) {
        int count = 0;
        List<Map<String, Object>> rows = apiPost(s, "/apiv2/employees/leavebalance",
            Map.of("company_id", s.string("companyId", ""), "employee_id", empId));
        for (Map<String, Object> raw : rows) {
            String type = str(raw, "leave_type");
            if (type == null) continue;
            LeaveBalance lb = balances.findByDarwinEmpIdAndLeaveType(empId, type).orElse(new LeaveBalance());
            lb.setUser(user);
            lb.setDarwinEmpId(empId);
            lb.setLeaveType(type);
            lb.setTotalDays(toBD(raw.getOrDefault("total", raw.getOrDefault("total_days", 0))));
            lb.setTakenDays(toBD(raw.getOrDefault("taken", raw.getOrDefault("taken_days", 0))));
            lb.setPendingDays(toBD(raw.getOrDefault("pending", raw.getOrDefault("pending_days", 0))));
            lb.setRemainingDays(toBD(raw.getOrDefault("remaining", raw.getOrDefault("remaining_days", 0))));
            lb.setSyncedAt(LocalDateTime.now());
            balances.save(lb); count++;
        }
        return count;
    }

    // ── Attendance ────────────────────────────────────────────────────────────

    private int upsertAttendance(HrmsSettings s, String empId, LocalDate from, LocalDate to,
                                  com.orbit.domain.client.AppUser user) {
        int page = 1, count = 0;
        while (true) {
            List<Map<String, Object>> rows = apiPost(s, "/apiv2/employees/attendance",
                Map.of("company_id", s.string("companyId", ""), "employee_id", empId,
                       "from_date", from.toString(), "to_date", to.toString(),
                       "page", page, "per_page", PAGE_SIZE));
            if (rows.isEmpty()) break;
            for (Map<String, Object> raw : rows) {
                LocalDate date = parseDate(str(raw, "date"));
                if (date == null) continue;
                AttendanceRecord ar = attendance
                    .findByDarwinEmpIdAndAttendanceDate(empId, date)
                    .orElse(new AttendanceRecord());
                ar.setUser(user);
                ar.setDarwinEmpId(empId);
                ar.setAttendanceDate(date);
                ar.setCheckIn(parseTime(str(raw, "check_in")));
                ar.setCheckOut(parseTime(str(raw, "check_out")));
                ar.setWorkingHours(toBD(raw.getOrDefault("working_hours", 0)));
                ar.setStatus(str(raw, "status"));
                ar.setSyncedAt(LocalDateTime.now());
                attendance.save(ar); count++;
            }
            if (rows.size() < PAGE_SIZE) break;
            page++;
        }
        return count;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private String baseUrl(HrmsSettings s) {
        String url = s.string("baseUrl", "");
        return url.stripTrailing().replaceAll("/$", "");
    }

    private List<Map<String, Object>> apiPost(HrmsSettings s, String path, Map<String, Object> body) {
        try {
            HttpHeaders headers = buildHeaders(s);
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<?, ?> resp = http.exchange(
                baseUrl(s) + path, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class).getBody();
            return unwrapData(resp);
        } catch (Exception e) {
            log.warn("Darwinbox POST {} failed: {}", path, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> apiGet(HrmsSettings s, String path, Map<String, String> params) {
        try {
            StringBuilder url = new StringBuilder(baseUrl(s) + path + "?");
            params.forEach((k, v) -> url.append(k).append("=").append(v).append("&"));
            Map<?, ?> resp = http.exchange(
                url.toString(), HttpMethod.GET,
                new HttpEntity<>(buildHeaders(s)), Map.class).getBody();
            return unwrapData(resp);
        } catch (Exception e) {
            log.warn("Darwinbox GET {} failed: {}", path, e.getMessage());
            return Collections.emptyList();
        }
    }

    private HttpHeaders buildHeaders(HrmsSettings s) {
        HttpHeaders h = new HttpHeaders();
        h.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        String apiKey = s.string("apiKey", "");
        switch (s.string("authType", "API_KEY").toUpperCase()) {
            case "BEARER" -> h.set("Authorization", "Bearer " + apiKey);
            case "API_KEY" -> h.set("x-api-key", apiKey);
            // HMAC: future implementation — requires request signing with timestamp + secret
            default       -> h.set("x-api-key", apiKey);
        }
        return h;
    }

    /** Unwrap Darwinbox envelope: { "status": true, "data": [...] } */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> unwrapData(Map<?, ?> resp) {
        if (resp == null) return Collections.emptyList();
        Object data = resp.get("data");
        if (data instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return Collections.emptyList();
    }

    // ── Webhook event processing ─────────────────────────────────────────────

    /**
     * Darwinbox sends JSON payloads for leave_applied, leave_approved,
     * leave_cancelled, wfh_applied, wfh_approved, employee_updated events.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void processWebhookEvent(HrmsSettings s, Map<String, Object> payload) {
        String eventType = str(payload, "event_type");
        if (eventType == null) { log.warn("Darwinbox webhook: missing event_type"); return; }
        log.info("Darwinbox webhook event: {}", eventType);

        switch (eventType) {
            case "leave_applied", "leave_approved", "leave_cancelled", "leave_rejected" -> {
                Map<String, Object> data = (Map<String, Object>) payload.get("data");
                if (data == null) return;
                String empId = str(data, "employee_id");
                users.findAll().stream()
                    .filter(u -> empId.equals(u.getDarwinEmpId()))
                    .findFirst()
                    .ifPresent(u -> {
                        try { upsertLeaves(s, empId, LocalDate.now().minusDays(7), LocalDate.now().plusDays(90), u); }
                        catch (Exception e) { log.warn("Webhook leave upsert failed: {}", e.getMessage()); }
                    });
            }
            case "wfh_applied", "wfh_approved", "wfh_cancelled" -> {
                Map<String, Object> data = (Map<String, Object>) payload.get("data");
                if (data == null) return;
                String empId = str(data, "employee_id");
                users.findAll().stream()
                    .filter(u -> empId.equals(u.getDarwinEmpId()))
                    .findFirst()
                    .ifPresent(u -> {
                        try { upsertWfh(s, empId, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), u); }
                        catch (Exception e) { log.warn("Webhook WFH upsert failed: {}", e.getMessage()); }
                    });
            }
            case "employee_updated", "employee_created" -> syncEmployeeDirectory(s);
            default -> log.debug("Darwinbox webhook: unhandled event {}", eventType);
        }
    }

    // ── Type / value helpers ──────────────────────────────────────────────────

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString().strip() : null;
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.substring(0, 10)); } catch (Exception e) { return null; }
    }

    private static LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalTime.parse(s.length() == 5 ? s + ":00" : s.substring(0, 8)); } catch (Exception e) { return null; }
    }

    private static int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return (int) Double.parseDouble(v.toString()); } catch (Exception e) { return 0; }
    }

    private static BigDecimal toBD(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    /** Darwinbox status values → our canonical values */
    static String normaliseStatus(String s) {
        if (s == null) return "PENDING";
        return switch (s.toLowerCase().strip()) {
            case "approved", "approve" -> "APPROVED";
            case "rejected", "reject"  -> "REJECTED";
            case "cancelled", "cancel" -> "CANCELLED";
            default                    -> "PENDING";
        };
    }

    static String mapWfhType(String s) {
        if (s == null) return "FULL_DAY";
        return switch (s.toLowerCase().replace("-","_").replace(" ","_")) {
            case "half_day_am", "first_half"  -> "HALF_DAY_AM";
            case "half_day_pm", "second_half" -> "HALF_DAY_PM";
            default                            -> "FULL_DAY";
        };
    }
}
