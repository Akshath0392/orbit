package com.orbit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.client.Client;
import com.orbit.domain.client.Portfolio;
import com.orbit.domain.client.Project;
import com.orbit.repository.ClientRepository;
import com.orbit.repository.PortfolioRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.security.JwtService;
import com.orbit.service.ProjectHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = PortfolioController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class PortfolioControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean PortfolioRepository portfolios;
    @MockBean ClientRepository clients;
    @MockBean ProjectRepository projects;
    @MockBean com.orbit.service.dashboard.PortfolioDashboardService dashboard;
    @MockBean JwtService jwtService;
    @MockBean ProjectHealthService healthService;
    @MockBean com.orbit.repository.ProjectReleaseRepository    releases;
    @MockBean com.orbit.repository.GovernanceMeetingRepository governance;

    private static Portfolio portfolio(String name, Long id, Client... clientList) {
        Portfolio p = new Portfolio(); p.setName(name);
        p.setClients(new LinkedHashSet<>(List.of(clientList)));
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void listReturnsAllPortfolios() throws Exception {
        Client c = new Client(); c.setName("Nexus Corp");
        Portfolio p = portfolio("CRM Platform", 1L, c);

        when(portfolios.findByActiveTrue()).thenReturn(List.of(p));

        mvc.perform(get("/api/v1/portfolios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("CRM Platform"))
            .andExpect(jsonPath("$[0].clientNames[0]").value("Nexus Corp"));
    }

    @Test
    void listByClientIdFilters() throws Exception {
        when(portfolios.findByClientsIdAndActiveTrue(1L)).thenReturn(List.of());

        mvc.perform(get("/api/v1/portfolios").param("clientId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createPortfolioReturnsId() throws Exception {
        Client c = new Client(); c.setName("Test Corp");
        Portfolio saved = portfolio("New Portfolio", 5L, c);

        when(clients.findById(1L)).thenReturn(Optional.of(c));
        when(portfolios.save(any())).thenReturn(saved);

        mvc.perform(post("/api/v1/portfolios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "New Portfolio", "clientIds", List.of(1)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void deletePortfolioDeactivates() throws Exception {
        Portfolio p = new Portfolio(); p.setName("Old");
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", 2L);
        when(portfolios.findById(2L)).thenReturn(Optional.of(p));

        mvc.perform(delete("/api/v1/portfolios/2"))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteNonExistentPortfolioIsNoContent() throws Exception {
        when(portfolios.findById(999L)).thenReturn(Optional.empty());

        mvc.perform(delete("/api/v1/portfolios/999"))
            .andExpect(status().isNoContent());
    }

    // ── /{id}/summary ──────────────────────────────────────────────────────────

    @Test
    void summaryReturnsAggregatedCounts() throws Exception {
        Client c = new Client(); c.setName("Nexus Corp");
        Portfolio p = portfolio("CRM Platform", 1L, c);

        Project proj = new Project();
        ReflectionTestUtils.setField(proj, "id", 10L);
        proj.setName("CRM Core");

        when(dashboard.dashboard(1L)).thenReturn(Map.of("summary", Map.of(
            "id", 1L, "name", "CRM Platform", "projectCount", 1,
            "totalCrs", 5L, "openBugs", 2L)));

        mvc.perform(get("/api/v1/portfolios/1/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("CRM Platform"))
            .andExpect(jsonPath("$.projectCount").value(1))
            .andExpect(jsonPath("$.totalCrs").value(5))
            .andExpect(jsonPath("$.openBugs").value(2));
    }

    @Test
    void summaryReturnsNotFoundForUnknownPortfolio() throws Exception {
        when(dashboard.dashboard(999L)).thenReturn(null);

        mvc.perform(get("/api/v1/portfolios/999/summary"))
            .andExpect(status().isNotFound());
    }

    @Test
    void summaryWithNoProjectsReturnsZeroCounts() throws Exception {
        Client c = new Client(); c.setName("Acme");
        Portfolio p = portfolio("Empty Portfolio", 2L, c);

        when(dashboard.dashboard(2L)).thenReturn(Map.of("summary", Map.of(
            "id", 2L, "name", "Empty Portfolio", "projectCount", 0,
            "totalCrs", 0L, "openBugs", 0L)));

        mvc.perform(get("/api/v1/portfolios/2/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectCount").value(0))
            .andExpect(jsonPath("$.totalCrs").value(0))
            .andExpect(jsonPath("$.openBugs").value(0));
    }
}
