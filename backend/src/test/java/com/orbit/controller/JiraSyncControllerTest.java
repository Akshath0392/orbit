package com.orbit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.config.JiraConfig;
import com.orbit.repository.JiraConfigRepository;
import com.orbit.repository.JiraSyncRunRepository;
import com.orbit.security.JwtService;
import com.orbit.service.sync.JiraSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = JiraSyncController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class JiraSyncControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean JiraSyncRunRepository syncRuns;
    @MockBean JiraConfigRepository jiraConfigs;
    @MockBean com.orbit.repository.ProjectRepository projects;
    @MockBean JiraSyncService jiraSyncService;
    @MockBean com.orbit.service.sync.ChangelogBackfillService changelogBackfill;
    @MockBean JwtService jwtService;

    // ── GET /config ───────────────────────────────────────────────────────────

    @Test
    void getConfigReturnsEmptyDefaults_whenNoRowExists() throws Exception {
        when(jiraConfigs.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/jira-sync/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.baseUrl").value(""))
            .andExpect(jsonPath("$.email").value(""))
            .andExpect(jsonPath("$.apiTokenSet").value(false))
            .andExpect(jsonPath("$.webhookUrl").isString())
            .andExpect(jsonPath("$.webhookEvents").isString());
    }

    @Test
    void getConfigReturnsMaskedFields_whenConfigured() throws Exception {
        JiraConfig cfg = new JiraConfig();
        cfg.setBaseUrl("https://myorg.atlassian.net");
        cfg.setEmail("admin@myorg.com");
        cfg.setApiToken("secret-api-token-value");
        cfg.setWebhookSecret("my-webhook-secret");
        cfg.setUpdatedAt(LocalDateTime.now());
        cfg.setUpdatedBy("admin@orbit.io");
        when(jiraConfigs.findFirstByOrderByIdAsc()).thenReturn(Optional.of(cfg));

        mvc.perform(get("/api/v1/jira-sync/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.baseUrl").value("https://myorg.atlassian.net"))
            .andExpect(jsonPath("$.email").value("admin@myorg.com"))
            .andExpect(jsonPath("$.apiTokenSet").value(true))
            // Webhook secret is masked — starts with first 2 chars then dots
            .andExpect(jsonPath("$.webhookSecret").value("my••••••••••"))
            .andExpect(jsonPath("$.webhookUrl").value("https://orbit.internal/api/jira/webhook"));
    }

    @Test
    void getConfigApiTokenSet_isFalse_whenTokenBlank() throws Exception {
        JiraConfig cfg = new JiraConfig();
        cfg.setApiToken("   ");
        when(jiraConfigs.findFirstByOrderByIdAsc()).thenReturn(Optional.of(cfg));

        mvc.perform(get("/api/v1/jira-sync/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.apiTokenSet").value(false));
    }

    // ── PUT /config ───────────────────────────────────────────────────────────

    @Test
    void saveConfigReturnsSavedTrue() throws Exception {
        when(jiraConfigs.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(jiraConfigs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(put("/api/v1/jira-sync/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "baseUrl",       "https://myorg.atlassian.net",
                    "email",         "admin@myorg.com",
                    "apiToken",      "new-real-token",
                    "webhookSecret", "new-secret"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saved").value(true));
    }

    @Test
    void saveConfig_doesNotOverwriteToken_whenMaskedValueSent() throws Exception {
        JiraConfig existing = new JiraConfig();
        existing.setApiToken("original-token");
        when(jiraConfigs.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));
        when(jiraConfigs.save(any())).thenAnswer(inv -> {
            JiraConfig saved = inv.getArgument(0);
            // Token must not be overwritten by a masked placeholder
            org.assertj.core.api.Assertions.assertThat(saved.getApiToken()).isEqualTo("original-token");
            return saved;
        });

        mvc.perform(put("/api/v1/jira-sync/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "baseUrl",  "https://myorg.atlassian.net",
                    "email",    "admin@myorg.com",
                    "apiToken", "••••••••••"   // masked placeholder — must be ignored
                ))))
            .andExpect(status().isOk());
    }

    @Test
    void saveConfig_updatesBaseUrlAndEmail_withoutToken() throws Exception {
        JiraConfig existing = new JiraConfig();
        existing.setBaseUrl("old-url");
        existing.setEmail("old@email.com");
        existing.setApiToken("keep-this-token");
        when(jiraConfigs.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));
        when(jiraConfigs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(put("/api/v1/jira-sync/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "baseUrl", "https://new.atlassian.net",
                    "email",   "new@myorg.com"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saved").value(true));
    }

    // ── GET /runs — page envelope ────────────────────────────────────────────

    @Test
    void runsReturnsPageEnvelope_withProgressScopeAndAttribution() throws Exception {
        com.orbit.domain.config.JiraSyncRun run = new com.orbit.domain.config.JiraSyncRun();
        org.springframework.test.util.ReflectionTestUtils.setField(run, "id", 42L);
        run.setSyncType("Full");
        run.setStatus("Running");
        run.setStartedAt(LocalDateTime.of(2026, 9, 3, 10, 15, 0));
        run.setIssuesProcessed(120);
        run.setProjectId(7L);
        run.setTriggeredBy("admin@orbit.io");
        run.setTotalExpected(500);
        run.setProcessedSoFar(120);
        run.setProjectScope("CRM Core, Collections 2.0");
        run.setCurrentProject("Collections 2.0");

        when(syncRuns.findAll(any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(
                java.util.List.of(run),
                org.springframework.data.domain.PageRequest.of(0, 20), 41));
        com.orbit.domain.client.Project project = new com.orbit.domain.client.Project();
        org.springframework.test.util.ReflectionTestUtils.setField(project, "id", 7L);
        org.springframework.test.util.ReflectionTestUtils.setField(project, "name", "CRM Core");
        when(projects.findAllById(java.util.List.of(7L))).thenReturn(java.util.List.of(project));

        mvc.perform(get("/api/v1/jira-sync/runs?page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.totalElements").value(41))
            .andExpect(jsonPath("$.content[0].id").value(42))
            .andExpect(jsonPath("$.content[0].type").value("Full"))
            .andExpect(jsonPath("$.content[0].status").value("Running"))
            .andExpect(jsonPath("$.content[0].projectId").value(7))
            .andExpect(jsonPath("$.content[0].projectName").value("CRM Core"))
            .andExpect(jsonPath("$.content[0].triggeredBy").value("admin@orbit.io"))
            .andExpect(jsonPath("$.content[0].totalExpected").value(500))
            .andExpect(jsonPath("$.content[0].processedSoFar").value(120))
            .andExpect(jsonPath("$.content[0].pending").value(380))
            .andExpect(jsonPath("$.content[0].projectScope[0]").value("CRM Core"))
            .andExpect(jsonPath("$.content[0].projectScope[1]").value("Collections 2.0"))
            .andExpect(jsonPath("$.content[0].currentProject").value("Collections 2.0"));
    }

    @Test
    void runsHistoricalRow_hasNullProgressAndScope() throws Exception {
        com.orbit.domain.config.JiraSyncRun run = new com.orbit.domain.config.JiraSyncRun();
        run.setSyncType("Delta");
        run.setStatus("Success");
        run.setStartedAt(LocalDateTime.of(2026, 9, 1, 8, 0, 0));
        run.setIssuesProcessed(17);
        when(syncRuns.findAll(any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(
                java.util.List.of(run),
                org.springframework.data.domain.PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/jira-sync/runs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].issues").value(17))
            .andExpect(jsonPath("$.content[0].totalExpected").doesNotExist())
            .andExpect(jsonPath("$.content[0].pending").doesNotExist())
            .andExpect(jsonPath("$.content[0].projectScope").doesNotExist())
            .andExpect(jsonPath("$.content[0].currentProject").doesNotExist());
    }

    // ── Backwards-compat /webhook-config ─────────────────────────────────────

    @Test
    void webhookConfigEndpointStillWorks() throws Exception {
        when(jiraConfigs.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/jira-sync/webhook-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.webhookUrl").isString());
    }
}
