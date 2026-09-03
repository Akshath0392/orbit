package com.orbit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.client.AppUser;
import com.orbit.domain.config.RoleScreenConfig;
import com.orbit.repository.*;
import com.orbit.security.JwtService;
import com.orbit.service.ProjectHealthService;
import com.orbit.service.SlaService;
import com.orbit.domain.client.Portfolio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = AdminController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class AdminControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean JwtService jwtService;
    @MockBean SlaRuleRepository slaRules;
    @MockBean LifecycleMappingRepository lifecycleMappings;
    @MockBean AppUserRepository users;
    @MockBean ClientRepository clients;
    @MockBean PortfolioRepository portfolios;
    @MockBean ProjectRepository projects;
    @MockBean JiraIssueRepository jiraIssues;
    @MockBean RoleScreenConfigRepository roleConfigs;
    @MockBean TeamRoleLabelRepository teamRoleLabels;
    @MockBean PasswordEncoder passwordEncoder;
    @MockBean SlaService slaService;
    @MockBean ProjectHealthService healthService;
    @MockBean com.orbit.service.StageCatalogService stageCatalog;

    // ── Roles ────────────────────────────────────────────────────────────────

    @Test
    void getRolesReturnsAllRoles() throws Exception {
        RoleScreenConfig r = new RoleScreenConfig();
        r.setRoleName("PJM"); r.setDisplayName("PJM"); r.setScreenIds("cockpit,cr,bugs");
        when(roleConfigs.findAll()).thenReturn(List.of(r));

        mvc.perform(get("/api/v1/admin/roles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].roleName").value("PJM"))
            .andExpect(jsonPath("$[0].screenIds").isArray());
    }

    @Test
    void getRolesScreenIdsIsSplit() throws Exception {
        RoleScreenConfig r = new RoleScreenConfig();
        r.setRoleName("ADMIN"); r.setDisplayName("Admin"); r.setScreenIds("radar,cockpit,admin");
        when(roleConfigs.findAll()).thenReturn(List.of(r));

        mvc.perform(get("/api/v1/admin/roles"))
            .andExpect(jsonPath("$[0].screenIds.length()").value(3));
    }

    // ── Role chart config ────────────────────────────────────────────────────

    @Test
    void getRolesIncludesChartConfigDefaultingToEmpty() throws Exception {
        RoleScreenConfig bare = new RoleScreenConfig();
        bare.setRoleName("PJM"); bare.setScreenIds("cr");
        RoleScreenConfig configured = new RoleScreenConfig();
        configured.setRoleName("EXEC"); configured.setScreenIds("radar");
        configured.setChartConfig(Map.of("chartType", "bar", "palette", "vibrant"));
        when(roleConfigs.findAll()).thenReturn(List.of(bare, configured));

        mvc.perform(get("/api/v1/admin/roles"))
            .andExpect(jsonPath("$[0].chartConfig").isEmpty())
            .andExpect(jsonPath("$[1].chartConfig.chartType").value("bar"))
            .andExpect(jsonPath("$[1].chartConfig.palette").value("vibrant"));
    }

    @Test
    void updateRoleChartConfigStoresValidatedValues() throws Exception {
        RoleScreenConfig r = new RoleScreenConfig();
        r.setRoleName("EXEC");
        when(roleConfigs.findByRoleName("EXEC")).thenReturn(Optional.of(r));
        when(roleConfigs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(put("/api/v1/admin/roles/EXEC/chart-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chartType\":\"stacked\",\"breakdownChartType\":\"line\",\"palette\":\"vibrant\",\"runtimeToggle\":\"on\"}"))
            .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(r.getChartConfig())
            .containsEntry("chartType", "stacked")
            .containsEntry("breakdownChartType", "line")
            .containsEntry("palette", "vibrant")
            .containsEntry("runtimeToggle", "on");

        // {} clears the preference back to defaults (stored as null)
        mvc.perform(put("/api/v1/admin/roles/EXEC/chart-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(r.getChartConfig()).isNull();
    }

    @Test
    void updateRoleChartConfigRejectsUnknownKeysAndValues() throws Exception {
        when(roleConfigs.findByRoleName("EXEC")).thenReturn(Optional.of(new RoleScreenConfig()));

        mvc.perform(put("/api/v1/admin/roles/EXEC/chart-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chartType\":\"pie\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/roles/EXEC/chart-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runtimeToggle\":\"maybe\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/roles/EXEC/chart-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fontSize\":\"large\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/roles/NOPE/chart-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chartType\":\"bar\"}"))
            .andExpect(status().isNotFound());
    }

    // ── Team role labels ─────────────────────────────────────────────────────

    @Test
    void teamRoleLabelsReturnCanonicalOrder() throws Exception {
        when(teamRoleLabels.findAll()).thenReturn(List.of(
            new com.orbit.domain.account.TeamRoleLabel("internal_sol", "Delivery Manager"),
            new com.orbit.domain.account.TeamRoleLabel("internal_pm", "Project Manager")));

        mvc.perform(get("/api/v1/admin/team-role-labels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].roleKey").value("internal_pm"))
            .andExpect(jsonPath("$[1].roleKey").value("internal_sol"))
            .andExpect(jsonPath("$[1].label").value("Delivery Manager"));
    }

    @Test
    void updateTeamRoleLabelPersistsTrimmedLabel() throws Exception {
        var existing = new com.orbit.domain.account.TeamRoleLabel("internal_pm", "Project Manager");
        when(teamRoleLabels.findById("internal_pm")).thenReturn(Optional.of(existing));
        when(teamRoleLabels.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(put("/api/v1/admin/team-role-labels/internal_pm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("label", "  Delivery Lead  "))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("Delivery Lead"));
    }

    @Test
    void updateTeamRoleLabelRejectsBlankAndUnknownKey() throws Exception {
        mvc.perform(put("/api/v1/admin/team-role-labels/internal_pm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("label", "  "))))
            .andExpect(status().isBadRequest());

        when(teamRoleLabels.findById("nope")).thenReturn(Optional.empty());
        mvc.perform(put("/api/v1/admin/team-role-labels/nope")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("label", "X"))))
            .andExpect(status().isNotFound());
    }

    // ── Bulk user import ──────────────────────────────────────────────────────

    @Test
    void bulkImportCreatesNewUsers() throws Exception {
        when(users.findByEmail("new@orbit.io")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hashed");
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roleConfigs.findByRoleName("PM")).thenReturn(Optional.of(new RoleScreenConfig()));

        List<Map<String, Object>> rows = List.of(
            Map.of("name", "New User", "email", "new@orbit.io", "role", "PM")
        );

        mvc.perform(post("/api/v1/admin/users/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(rows)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processed").value(1))
            .andExpect(jsonPath("$.results[0].status").value("created"));
    }

    @Test
    void bulkImportSkipsExistingUsers() throws Exception {
        AppUser existing = new AppUser();
        existing.setEmail("existing@orbit.io");
        when(users.findByEmail("existing@orbit.io")).thenReturn(Optional.of(existing));

        List<Map<String, Object>> rows = List.of(
            Map.of("name", "Existing", "email", "existing@orbit.io", "role", "PM")
        );

        mvc.perform(post("/api/v1/admin/users/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(rows)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].status").value("skipped_exists"));
    }

    @Test
    void bulkImportIgnoresRowsWithNoEmail() throws Exception {
        List<Map<String, Object>> rows = List.of(
            Map.of("name", "No Email User", "role", "PM")
        );

        mvc.perform(post("/api/v1/admin/users/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(rows)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processed").value(0));
    }

    // ── SLA rules ─────────────────────────────────────────────────────────────

    @Test
    void getSlaRulesReturnsEmpty() throws Exception {
        when(slaRules.findAllByOrderByClientIdAscSeverityAsc()).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/sla-rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
