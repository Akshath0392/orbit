package com.orbit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.hrms.HrmsSyncRun;
import com.orbit.domain.darwin.LeaveRecord;
import com.orbit.domain.darwin.WfhRecord;
import com.orbit.domain.client.AppUser;
import com.orbit.repository.AppUserRepository;
import com.orbit.repository.AttendanceRecordRepository;
import com.orbit.repository.HrmsSyncRunRepository;
import com.orbit.repository.LeaveBalanceRepository;
import com.orbit.repository.LeaveRecordRepository;
import com.orbit.repository.WfhRecordRepository;
import com.orbit.security.JwtService;
import com.orbit.service.hrms.HrmsSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = HrmsSyncController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class HrmsSyncControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean JwtService                 jwtService;
    @MockBean HrmsSyncService            hrms;
    @MockBean HrmsSyncRunRepository      syncRuns;
    @MockBean LeaveRecordRepository      leaves;
    @MockBean LeaveBalanceRepository     balances;
    @MockBean AttendanceRecordRepository attendance;
    @MockBean WfhRecordRepository        wfhRecords;
    @MockBean AppUserRepository          users;

    // ── helpers ───────────────────────────────────────────────────────────────

    private AppUser user(String name, String email) {
        AppUser u = new AppUser();
        try {
            var fName = AppUser.class.getDeclaredField("name"); fName.setAccessible(true); fName.set(u, name);
            var fEmail = AppUser.class.getDeclaredField("email"); fEmail.setAccessible(true); fEmail.set(u, email);
        } catch (Exception ignored) {}
        return u;
    }

    private LeaveRecord leave(String name, String email, String type, LocalDate from, LocalDate to, String status) {
        LeaveRecord l = new LeaveRecord();
        l.setUser(user(name, email));
        l.setDarwinEmpId("EMP001");
        l.setDarwinLeaveId("DBX-L-" + name);
        l.setLeaveType(type);
        l.setStartDate(from);
        l.setEndDate(to);
        l.setWorkingDays(1);
        l.setStatus(status);
        l.setSyncedAt(LocalDateTime.now());
        return l;
    }

    private WfhRecord wfh(String name, String email, LocalDate date, String wfhType, String status) {
        WfhRecord w = new WfhRecord();
        w.setUser(user(name, email));
        w.setDarwinEmpId("EMP001");
        w.setDarwinWfhId("DBX-WFH-" + name);
        w.setWfhDate(date);
        w.setWfhType(wfhType);
        w.setStatus(status);
        w.setReason("Focus work");
        w.setSyncedAt(LocalDateTime.now());
        return w;
    }

    private HrmsSyncRun syncRun(String type, String status, int pulled) {
        HrmsSyncRun r = new HrmsSyncRun();
        r.setSyncType(type); r.setStatus(status); r.setRecordsPulled(pulled);
        r.setStartedAt(LocalDateTime.now().minusMinutes(5));
        r.setCompletedAt(LocalDateTime.now());
        return r;
    }

    // ── GET /providers ────────────────────────────────────────────────────────

    @Test
    void providersListsRegisteredConnectorsWithDescriptors() throws Exception {
        when(hrms.providers()).thenReturn(List.of(Map.of(
            "key", "acmehr",
            "name", "AcmeHR",
            "fields", List.of(Map.of("key", "baseUrl", "label", "Tenant URL", "type", "url",
                                     "required", true, "secret", false))
        )));

        mvc.perform(get("/api/v1/hrms/providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].key").value("acmehr"))
            .andExpect(jsonPath("$[0].name").value("AcmeHR"))
            .andExpect(jsonPath("$[0].fields[0].key").value("baseUrl"))
            .andExpect(jsonPath("$[0].fields[0].required").value(true));
    }

    // ── GET /status ───────────────────────────────────────────────────────────

    @Test
    void statusReturnsConnectionInfo() throws Exception {
        when(hrms.getConnectionStatus()).thenReturn(
            Map.of("provider", "acmehr", "enabled", false, "configured", false)
        );

        mvc.perform(get("/api/v1/hrms/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("acmehr"))
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.configured").value(false));
    }

    // ── GET /runs ─────────────────────────────────────────────────────────────

    @Test
    void runsReturnsSyncHistory() throws Exception {
        when(syncRuns.findTop20ByOrderByStartedAtDesc()).thenReturn(
            List.of(syncRun("DELTA", "SUCCESS", 12), syncRun("FULL", "SUCCESS", 45))
        );

        mvc.perform(get("/api/v1/hrms/runs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].type").value("DELTA"))
            .andExpect(jsonPath("$[0].status").value("SUCCESS"))
            .andExpect(jsonPath("$[0].recordsPulled").value(12))
            .andExpect(jsonPath("$[1].type").value("FULL"))
            .andExpect(jsonPath("$[1].recordsPulled").value(45));
    }

    @Test
    void runsReturnsEmptyListWhenNoHistory() throws Exception {
        when(syncRuns.findTop20ByOrderByStartedAtDesc()).thenReturn(List.of());

        mvc.perform(get("/api/v1/hrms/runs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ── POST /sync ────────────────────────────────────────────────────────────

    @Test
    void triggerSyncDeltaReturnsSuccess() throws Exception {
        when(hrms.triggerSync("DELTA")).thenReturn(
            Map.of("status", "SUCCESS", "recordsPulled", 7)
        );

        mvc.perform(post("/api/v1/hrms/sync?type=DELTA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.recordsPulled").value(7));
    }

    @Test
    void triggerSyncReturns500OnError() throws Exception {
        when(hrms.triggerSync(anyString())).thenThrow(new RuntimeException("Connection refused"));

        mvc.perform(post("/api/v1/hrms/sync?type=FULL"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("Connection refused"));
    }

    // ── POST /test ────────────────────────────────────────────────────────────

    @Test
    void testConnectionDelegatesToActiveConnector() throws Exception {
        when(hrms.testConnection()).thenReturn(Map.of("ok", true, "message", "Connected"));

        mvc.perform(post("/api/v1/hrms/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void testConnectionReportsMissingProvider() throws Exception {
        when(hrms.testConnection()).thenReturn(Map.of("ok", false, "error", "No HRMS provider configured"));

        mvc.perform(post("/api/v1/hrms/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(false))
            .andExpect(jsonPath("$.error").value("No HRMS provider configured"));
    }

    // ── GET /leaves ───────────────────────────────────────────────────────────

    @Test
    void leavesReturnsUpcomingByDefault() throws Exception {
        LocalDate today = LocalDate.now();
        when(leaves.findUpcoming(today)).thenReturn(List.of(
            leave("Priya K.", "priya@orbit.io", "Annual Leave", today.plusDays(1), today.plusDays(5), "APPROVED"),
            leave("Dev L.",   "dev@orbit.io",   "Sick Leave",   today,             today,              "APPROVED")
        ));

        mvc.perform(get("/api/v1/hrms/leaves"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Priya K."))
            .andExpect(jsonPath("$[0].leaveType").value("Annual Leave"))
            .andExpect(jsonPath("$[0].status").value("APPROVED"))
            .andExpect(jsonPath("$[1].name").value("Dev L."));
    }

    @Test
    void leavesFiltersByDateRange() throws Exception {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to   = LocalDate.of(2026, 7, 31);
        when(leaves.findByStartDateBetweenOrderByStartDateAsc(from, to)).thenReturn(
            List.of(leave("Amit S.", "amit@orbit.io", "Annual Leave", from, from.plusDays(4), "APPROVED"))
        );

        mvc.perform(get("/api/v1/hrms/leaves?from=2026-07-01&to=2026-07-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Amit S."));
    }

    // ── GET /wfh ─────────────────────────────────────────────────────────────

    @Test
    void wfhReturnsUpcomingByDefault() throws Exception {
        LocalDate today = LocalDate.now();
        when(wfhRecords.findUpcoming(today)).thenReturn(List.of(
            wfh("Priya K.", "priya@orbit.io", today.plusDays(1), "FULL_DAY",    "APPROVED"),
            wfh("Dev L.",   "dev@orbit.io",   today.plusDays(2), "HALF_DAY_AM", "PENDING")
        ));

        mvc.perform(get("/api/v1/hrms/wfh"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Priya K."))
            .andExpect(jsonPath("$[0].wfhType").value("FULL_DAY"))
            .andExpect(jsonPath("$[0].status").value("APPROVED"))
            .andExpect(jsonPath("$[1].wfhType").value("HALF_DAY_AM"))
            .andExpect(jsonPath("$[1].status").value("PENDING"));
    }

    @Test
    void wfhFiltersByDateRange() throws Exception {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to   = LocalDate.of(2026, 7, 7);
        when(wfhRecords.findByWfhDateBetweenOrderByWfhDateAsc(from, to)).thenReturn(
            List.of(wfh("Kavitha R.", "kavitha@orbit.io", from, "FULL_DAY", "APPROVED"))
        );

        mvc.perform(get("/api/v1/hrms/wfh?from=2026-07-01&to=2026-07-07"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Kavitha R."))
            .andExpect(jsonPath("$[0].wfhDate").exists());
    }

    @Test
    void wfhFiltersByStatus() throws Exception {
        when(wfhRecords.findByStatusInOrderByWfhDateAsc(List.of("PENDING"))).thenReturn(
            List.of(wfh("Amit S.", "amit@orbit.io", LocalDate.now().plusDays(3), "FULL_DAY", "PENDING"))
        );

        mvc.perform(get("/api/v1/hrms/wfh?status=PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void wfhReturnsEmptyWhenNoneFound() throws Exception {
        when(wfhRecords.findUpcoming(any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/hrms/wfh"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void wfhResponseIncludesReasonAndHrmsId() throws Exception {
        WfhRecord w = wfh("Priya K.", "priya@orbit.io", LocalDate.now().plusDays(1), "FULL_DAY", "APPROVED");
        when(wfhRecords.findUpcoming(any())).thenReturn(List.of(w));

        mvc.perform(get("/api/v1/hrms/wfh"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].reason").value("Focus work"))
            .andExpect(jsonPath("$[0].hrmsWfhId").value("DBX-WFH-Priya K."))
            .andExpect(jsonPath("$[0].syncedAt").exists());
    }

    // ── GET /config ───────────────────────────────────────────────────────────

    @Test
    void configReturnsProviderAndSettings() throws Exception {
        when(hrms.getConfigView()).thenReturn(Map.of(
            "provider",     "acmehr",
            "providerName", "AcmeHR",
            "enabled",      false,
            "settings",     Map.of("baseUrl", "https://acme.example.com", "authType", "API_KEY"),
            "secretsSet",   Map.of("apiKey", true, "webhookSecret", false)
        ));

        mvc.perform(get("/api/v1/hrms/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("acmehr"))
            .andExpect(jsonPath("$.settings.baseUrl").value("https://acme.example.com"))
            .andExpect(jsonPath("$.secretsSet.apiKey").value(true))
            .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void configReturnsEmptyWhenNoProviderConfigured() throws Exception {
        var view = new java.util.LinkedHashMap<String, Object>();
        view.put("provider", null);
        view.put("providerName", null);
        view.put("enabled", false);
        view.put("settings", Map.of());
        view.put("secretsSet", Map.of());
        when(hrms.getConfigView()).thenReturn(view);

        mvc.perform(get("/api/v1/hrms/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").doesNotExist())
            .andExpect(jsonPath("$.enabled").value(false));
    }

    // ── GET /employees ────────────────────────────────────────────────────────

    @Test
    void employeesListsAllUsersWithMappingStatus() throws Exception {
        AppUser u1 = user("Priya K.", "priya@orbit.io");
        AppUser u2 = user("Amit S.", "amit@orbit.io");
        try {
            var f = AppUser.class.getDeclaredField("darwinEmpId"); f.setAccessible(true);
            f.set(u1, "EMP001");
            // u2 has no HR employee ID — unmapped
        } catch (Exception ignored) {}
        when(users.findAll()).thenReturn(List.of(u1, u2));

        mvc.perform(get("/api/v1/hrms/employees"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Priya K."))
            .andExpect(jsonPath("$[0].hrmsEmpId").value("EMP001"))
            .andExpect(jsonPath("$[0].mapped").value(true))
            .andExpect(jsonPath("$[1].mapped").value(false));
    }

    // ── PATCH /employees/{id}/emp-id ──────────────────────────────────────────

    @Test
    void setEmpIdUpdatesMappingAndReturnsOk() throws Exception {
        AppUser u = user("Priya K.", "priya@orbit.io");
        try { var f = AppUser.class.getDeclaredField("id"); f.setAccessible(true); f.set(u, 1L); }
        catch (Exception ignored) {}
        when(users.findById(1L)).thenReturn(java.util.Optional.of(u));
        when(users.save(any())).thenReturn(u);

        mvc.perform(patch("/api/v1/hrms/employees/1/emp-id")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"hrmsEmpId\":\"EMP099\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.hrmsEmpId").value("EMP099"));
    }

    @Test
    void setEmpIdReturns404ForUnknownUser() throws Exception {
        when(users.findById(999L)).thenReturn(java.util.Optional.empty());

        mvc.perform(patch("/api/v1/hrms/employees/999/emp-id")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"hrmsEmpId\":\"EMP099\"}"))
            .andExpect(status().isNotFound());
    }
}
