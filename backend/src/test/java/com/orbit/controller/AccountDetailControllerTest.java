package com.orbit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.account.ProjectRisk;
import com.orbit.domain.client.Project;
import com.orbit.repository.GovernanceMeetingRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.repository.ProjectRiskRepository;
import com.orbit.repository.ProjectTeamRepository;
import com.orbit.repository.ProjectWinRepository;
import com.orbit.security.JwtService;
import com.orbit.service.AccountDetailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = AccountDetailController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class AccountDetailControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean AccountDetailService    service;
    @MockBean ProjectRepository       projects;
    @MockBean ProjectTeamRepository   teams;
    @MockBean ProjectRiskRepository       risks;
    @MockBean ProjectWinRepository        wins;
    @MockBean GovernanceMeetingRepository governance;
    @MockBean JwtService                  jwtService;

    @Test
    void detailReturnsAggregatorPayload() throws Exception {
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("id", 12L); payload.put("name", "Atlas Launch");
        payload.put("healthPct", 76); payload.put("stage", "HYPERCARE");
        payload.put("rag", "Amber");
        when(service.assemble(12L)).thenReturn(Optional.of(payload));

        mvc.perform(get("/api/v1/accounts/12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Atlas Launch"))
            .andExpect(jsonPath("$.healthPct").value(76))
            .andExpect(jsonPath("$.stage").value("HYPERCARE"))
            .andExpect(jsonPath("$.rag").value("Amber"));
    }

    @Test
    void detailReturns404ForUnknownProject() throws Exception {
        when(service.assemble(999L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/accounts/999"))
            .andExpect(status().isNotFound());
    }

    // ── Team save ────────────────────────────────────────────────────────────

    @Test
    void saveTeamUpsertsAndReturnsOk() throws Exception {
        Project p = new Project(); p.setName("Demo");
        when(projects.findById(5L)).thenReturn(Optional.of(p));
        when(teams.findByProjectId(5L)).thenReturn(Optional.empty());

        mvc.perform(put("/api/v1/accounts/5/team")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "internalPm", "Priya K.",
                    "clientSponsor", "Sanjay M."))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));

        verify(teams).save(any());
    }

    @Test
    void saveTeamReturns404ForUnknownProject() throws Exception {
        when(projects.findById(999L)).thenReturn(Optional.empty());

        mvc.perform(put("/api/v1/accounts/999/team")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"internalPm\":\"X\"}"))
            .andExpect(status().isNotFound());
    }

    // ── Risk register ────────────────────────────────────────────────────────

    @Test
    void addRiskCreatesRowAndReturnsId() throws Exception {
        Project p = new Project();
        when(projects.findById(5L)).thenReturn(Optional.of(p));
        when(risks.save(any())).thenAnswer(inv -> {
            ProjectRisk r = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(r, "id", 42L);
            return r;
        });

        mvc.perform(post("/api/v1/accounts/5/risks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "risk",        "Client pushback on UAT scope",
                    "rag",         "Amber",
                    "actionOwner", "Priya K.",
                    "receivedOn",  "2026-06-20",
                    "actionEnd",   "2026-06-30"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void addRiskReturns404ForUnknownProject() throws Exception {
        when(projects.findById(999L)).thenReturn(Optional.empty());
        mvc.perform(post("/api/v1/accounts/999/risks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"risk\":\"X\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteRiskRemovesMatchingRow() throws Exception {
        ProjectRisk r = new ProjectRisk();
        r.setProjectId(5L); r.setRisk("X");
        when(risks.findById(7L)).thenReturn(Optional.of(r));

        mvc.perform(delete("/api/v1/accounts/5/risks/7"))
            .andExpect(status().isNoContent());

        verify(risks).delete(r);
    }

    @Test
    void deleteRiskIgnoresRiskFromDifferentProject() throws Exception {
        ProjectRisk r = new ProjectRisk();
        r.setProjectId(99L); r.setRisk("X");  // wrong project
        when(risks.findById(7L)).thenReturn(Optional.of(r));

        mvc.perform(delete("/api/v1/accounts/5/risks/7"))
            .andExpect(status().isNoContent());

        // No delete because projectId mismatch
        verify(risks, org.mockito.Mockito.never()).delete(any());
    }

    // ── Ops model ────────────────────────────────────────────────────────────

    @Test
    void setOpsModelPersistsValidValue() throws Exception {
        Project p = new Project();
        when(projects.findById(5L)).thenReturn(Optional.of(p));

        mvc.perform(put("/api/v1/accounts/5/ops-model")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opsModel\":\"bau\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opsModel").value("bau"));

        verify(projects).save(p);
    }

    @Test
    void setOpsModelRejectsInvalidValue() throws Exception {
        Project p = new Project();
        when(projects.findById(5L)).thenReturn(Optional.of(p));

        mvc.perform(put("/api/v1/accounts/5/ops-model")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opsModel\":\"freestyle\"}"))
            .andExpect(status().isBadRequest());

        verify(projects, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void setOpsModelReturns404ForUnknownProject() throws Exception {
        when(projects.findById(999L)).thenReturn(Optional.empty());
        mvc.perform(put("/api/v1/accounts/999/ops-model")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opsModel\":\"launch\"}"))
            .andExpect(status().isNotFound());
    }
}
