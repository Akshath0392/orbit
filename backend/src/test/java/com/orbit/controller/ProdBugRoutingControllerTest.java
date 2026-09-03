package com.orbit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.client.Client;
import com.orbit.domain.client.Project;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.domain.routing.ProdBugQuarantine;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.service.sync.ProdBugBackfillService;
import com.orbit.repository.ClientRepository;
import com.orbit.repository.ProdBugQuarantineRepository;
import com.orbit.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase A read + config endpoints. RBAC (@PreAuthorize) isn't asserted here
 * because @WebMvcTest excludes security auto-config — that's covered end-to-end
 * in integration tests. These tests pin the JSON contract and the validation
 * on the config PUT.
 */
@WebMvcTest(
    value = ProdBugRoutingController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class ProdBugRoutingControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean ProjectRepository projects;
    @MockBean ClientRepository clients;
    @MockBean ProdBugQuarantineRepository quarantine;
    @MockBean com.orbit.repository.JiraIssueRepository jiraIssues;
    @MockBean com.orbit.service.sync.ProdBugBackfillService backfillService;
    @MockBean com.orbit.security.JwtService jwtService;

    private Project sharedProject() {
        Project p = new Project();
        ReflectionTestUtils.setField(p, "id", 42L);
        p.setName("POOL");
        p.setSharedProdBugs(true);
        p.setClientCodeField("customfield_11683");
        return p;
    }

    private Client client(long id, String name, String code) {
        Client c = new Client();
        ReflectionTestUtils.setField(c, "id", id);
        ReflectionTestUtils.setField(c, "name", name);
        ReflectionTestUtils.setField(c, "code", code);
        return c;
    }

    @Test
    void list_config_returns_shared_pool_with_open_quarantine_count() throws Exception {
        when(projects.findBySharedProdBugsTrue()).thenReturn(List.of(sharedProject()));
        when(quarantine.countOpen()).thenReturn(7L);

        mvc.perform(get("/api/v1/admin/prod-bug-routing/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].projectId").value(42))
            .andExpect(jsonPath("$[0].projectName").value("POOL"))
            .andExpect(jsonPath("$[0].isSharedProdBugs").value(true))
            .andExpect(jsonPath("$[0].clientCodeField").value("customfield_11683"))
            .andExpect(jsonPath("$[0].quarantinedOpen").value(7));
    }

    @Test
    void put_config_sets_shared_and_field() throws Exception {
        Project p = sharedProject();
        p.setSharedProdBugs(false);
        p.setClientCodeField(null);
        when(projects.findById(42L)).thenReturn(Optional.of(p));
        when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quarantine.countOpen()).thenReturn(0L);

        mvc.perform(put("/api/v1/admin/prod-bug-routing/config/42")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "isSharedProdBugs", true,
                    "clientCodeField", "customfield_11683"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isSharedProdBugs").value(true))
            .andExpect(jsonPath("$.clientCodeField").value("customfield_11683"));
    }

    @Test
    void put_config_rejects_shared_true_without_field() throws Exception {
        when(projects.findById(42L)).thenReturn(Optional.of(sharedProject()));

        mvc.perform(put("/api/v1/admin/prod-bug-routing/config/42")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("isSharedProdBugs", true))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void put_config_clears_field_when_shared_turned_off() throws Exception {
        Project p = sharedProject();
        when(projects.findById(42L)).thenReturn(Optional.of(p));
        when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quarantine.countOpen()).thenReturn(0L);

        mvc.perform(put("/api/v1/admin/prod-bug-routing/config/42")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "isSharedProdBugs", false,
                    "clientCodeField", "customfield_11683"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isSharedProdBugs").value(false))
            .andExpect(jsonPath("$.clientCodeField").doesNotExist());
    }

    @Test
    void put_config_404_when_project_missing() throws Exception {
        when(projects.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(put("/api/v1/admin/prod-bug-routing/config/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void list_clients_reports_codes_and_hasCode() throws Exception {
        when(clients.findAll()).thenReturn(List.of(
            client(1L, "Acme", "ACME"),
            client(2L, "Beetle", null),
            client(3L, "Citrus", "  ")
        ));
        mvc.perform(get("/api/v1/admin/prod-bug-routing/clients"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("ACME"))
            .andExpect(jsonPath("$[0].hasCode").value(true))
            .andExpect(jsonPath("$[1].hasCode").value(false))
            .andExpect(jsonPath("$[2].hasCode").value(false));
    }

    // ── Phase C: mutations ──────────────────────────────────────────────

    @Test
    void set_client_code_uppercases_and_persists() throws Exception {
        Client c = client(1L, "Acme", null);
        when(clients.findById(1L)).thenReturn(Optional.of(c));
        when(clients.findByCodeIgnoreCase("ACME")).thenReturn(Optional.empty());
        when(clients.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/v1/admin/prod-bug-routing/clients/1/code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("code", "acme"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("ACME"))
            .andExpect(jsonPath("$.hasCode").value(true));
    }

    @Test
    void set_client_code_rejects_blank() throws Exception {
        when(clients.findById(1L)).thenReturn(Optional.of(client(1L, "Acme", null)));
        mvc.perform(post("/api/v1/admin/prod-bug-routing/clients/1/code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("code", "   "))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void set_client_code_rejects_duplicate_across_clients() throws Exception {
        when(clients.findById(1L)).thenReturn(Optional.of(client(1L, "Acme", null)));
        when(clients.findByCodeIgnoreCase("BEETLE"))
            .thenReturn(Optional.of(client(2L, "Beetle Inc", "BEETLE")));

        mvc.perform(post("/api/v1/admin/prod-bug-routing/clients/1/code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("code", "beetle"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Beetle Inc")));
    }

    @Test
    void set_client_code_allows_same_client_re_saving_own_code() throws Exception {
        Client c = client(1L, "Acme", "ACME");
        when(clients.findById(1L)).thenReturn(Optional.of(c));
        when(clients.findByCodeIgnoreCase("ACME")).thenReturn(Optional.of(c));
        when(clients.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/v1/admin/prod-bug-routing/clients/1/code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("code", "acme"))))
            .andExpect(status().isOk());
    }

    @Test
    void resolve_quarantine_records_admin_and_note() throws Exception {
        ProdBugQuarantine q = new ProdBugQuarantine();
        ReflectionTestUtils.setField(q, "id", 3L);
        q.setJiraKey("POOL-3");
        q.setReason(ProdBugQuarantine.Reason.MISSING_CODE);
        q.setSeenAt(LocalDateTime.now().minusDays(1));
        q.setLastSeenAt(LocalDateTime.now());
        when(quarantine.findById(3L)).thenReturn(Optional.of(q));
        when(quarantine.save(any(ProdBugQuarantine.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/v1/admin/prod-bug-routing/quarantine/3/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("note", "Reporter fixed the field in Jira"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jiraKey").value("POOL-3"))
            .andExpect(jsonPath("$.resolutionNote").value("Reporter fixed the field in Jira"));
    }

    @Test
    void resolve_quarantine_assigns_client_when_code_provided() throws Exception {
        JiraIssue issue = new JiraIssue();
        ReflectionTestUtils.setField(issue, "id", 500L);
        ProdBugQuarantine q = new ProdBugQuarantine();
        ReflectionTestUtils.setField(q, "id", 4L);
        q.setJiraKey("POOL-4");
        q.setReason(ProdBugQuarantine.Reason.UNKNOWN_CODE);
        q.setJiraIssue(issue);
        when(quarantine.findById(4L)).thenReturn(Optional.of(q));
        when(quarantine.save(any(ProdBugQuarantine.class))).thenAnswer(inv -> inv.getArgument(0));

        Client acme = client(10L, "Acme", "ACME");
        when(clients.findActiveByCodeIgnoreCase("ACME")).thenReturn(Optional.of(acme));
        when(jiraIssues.save(any(JiraIssue.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/v1/admin/prod-bug-routing/quarantine/4/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "note", "typo", "assignClientCode", "ACME"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignedClientCode").value("ACME"));
    }

    @Test
    void resolve_quarantine_rejects_unknown_assign_code() throws Exception {
        ProdBugQuarantine q = new ProdBugQuarantine();
        ReflectionTestUtils.setField(q, "id", 5L);
        q.setJiraKey("POOL-5");
        q.setReason(ProdBugQuarantine.Reason.UNKNOWN_CODE);
        when(quarantine.findById(5L)).thenReturn(Optional.of(q));

        mvc.perform(post("/api/v1/admin/prod-bug-routing/quarantine/5/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("assignClientCode", "NONE"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void resolve_quarantine_rejects_code_held_only_by_inactive_client() throws Exception {
        // Assigning to a retired duplicate would re-hide the bug.
        ProdBugQuarantine q = new ProdBugQuarantine();
        ReflectionTestUtils.setField(q, "id", 7L);
        q.setJiraKey("POOL-7");
        q.setReason(ProdBugQuarantine.Reason.UNKNOWN_CODE);
        when(quarantine.findById(7L)).thenReturn(Optional.of(q));
        when(clients.findActiveByCodeIgnoreCase("OLDCO")).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/admin/prod-bug-routing/quarantine/7/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("assignClientCode", "OLDCO"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("no active client with code OLDCO"));
    }

    @Test
    void resolve_quarantine_400_when_already_resolved() throws Exception {
        ProdBugQuarantine q = new ProdBugQuarantine();
        ReflectionTestUtils.setField(q, "id", 6L);
        q.setResolvedAt(LocalDateTime.now().minusHours(1));
        when(quarantine.findById(6L)).thenReturn(Optional.of(q));

        mvc.perform(post("/api/v1/admin/prod-bug-routing/quarantine/6/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("note", "n/a"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void backfill_forwards_status_from_service() throws Exception {
        when(backfillService.backfill(42L)).thenReturn(Map.of(
            "status", "Success", "issuesProcessed", 150, "source", "BACKFILL"));

        mvc.perform(post("/api/v1/admin/prod-bug-routing/backfill/42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Success"))
            .andExpect(jsonPath("$.issuesProcessed").value(150));
    }

    @Test
    void backfill_404_for_missing_project() throws Exception {
        when(backfillService.backfill(99L)).thenReturn(Map.of(
            "status", "NOT_FOUND", "error", "Project 99 not found"));

        mvc.perform(post("/api/v1/admin/prod-bug-routing/backfill/99"))
            .andExpect(status().isNotFound());
    }

    @Test
    void backfill_400_when_project_not_shared_pool() throws Exception {
        when(backfillService.backfill(42L)).thenReturn(Map.of(
            "status", "REJECTED", "error", "not shared"));

        mvc.perform(post("/api/v1/admin/prod-bug-routing/backfill/42"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void list_quarantine_paginates_and_serialises_reason() throws Exception {
        ProdBugQuarantine q = new ProdBugQuarantine();
        ReflectionTestUtils.setField(q, "id", 5L);
        q.setJiraKey("POOL-777");
        q.setRawClientCode("UNKN");
        q.setReason(ProdBugQuarantine.Reason.UNKNOWN_CODE);
        q.setSeenAt(LocalDateTime.now().minusMinutes(30));
        q.setLastSeenAt(LocalDateTime.now());
        JiraIssue issue = new JiraIssue();
        ReflectionTestUtils.setField(issue, "summary", "Login screen freezes");
        q.setJiraIssue(issue);
        Page<ProdBugQuarantine> page = new PageImpl<>(List.of(q));
        when(quarantine.findOpen(any())).thenReturn(page);

        mvc.perform(get("/api/v1/admin/prod-bug-routing/quarantine"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].jiraKey").value("POOL-777"))
            .andExpect(jsonPath("$.content[0].jiraSummary").value("Login screen freezes"))
            .andExpect(jsonPath("$.content[0].reason").value("UNKNOWN_CODE"))
            .andExpect(jsonPath("$.content[0].rawClientCode").value("UNKN"));
    }
}
