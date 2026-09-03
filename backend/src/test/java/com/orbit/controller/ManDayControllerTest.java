package com.orbit.controller;

import com.orbit.domain.client.Client;
import com.orbit.domain.client.ManDayBudget;
import com.orbit.domain.client.Project;
import com.orbit.domain.capacity.ManDaySnapshot;
import com.orbit.repository.ManDayBudgetRepository;
import com.orbit.repository.ManDaySnapshotRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = ManDayController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class ManDayControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ManDayBudgetRepository budgets;
    @MockBean ManDaySnapshotRepository snapshots;
    @MockBean ProjectRepository projects;
    @MockBean JwtService jwtService;

    private Project project(long id, String name) {
        Client c = new Client(); c.setName("Nexus Corp");
        Project p = new Project();
        ReflectionTestUtils.setField(p, "id", id);
        p.setName(name);
        p.setClient(c);
        return p;
    }

    private ManDayBudget budget(Project p, double days) {
        ManDayBudget b = ManDayBudget.builder()
            .project(p)
            .purchasedDays(BigDecimal.valueOf(days))
            .build();
        return b;
    }

    private ManDaySnapshot snapshot(Project p, double burned) {
        ManDaySnapshot s = new ManDaySnapshot();
        ReflectionTestUtils.setField(s, "project", p);
        ReflectionTestUtils.setField(s, "burnedDays", BigDecimal.valueOf(burned));
        return s;
    }

    // ── /man-days/portfolio-summary ────────────────────────────────────────────

    @Test
    void portfolioSummaryAggregatesAcrossProjects() throws Exception {
        Project p1 = project(1L, "Alpha"); Project p2 = project(2L, "Beta");
        when(projects.findByPortfolioIdAndActiveTrue(10L)).thenReturn(List.of(p1, p2));
        when(budgets.findByProjectId(1L)).thenReturn(Optional.of(budget(p1, 100.0)));
        when(budgets.findByProjectId(2L)).thenReturn(Optional.of(budget(p2, 200.0)));
        when(snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(1L))
            .thenReturn(List.of(snapshot(p1, 60.0)));
        when(snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(2L))
            .thenReturn(List.of(snapshot(p2, 100.0)));

        mvc.perform(get("/api/v1/man-days/portfolio-summary").param("portfolioId", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.portfolioId").value(10))
            .andExpect(jsonPath("$.soldMandays").value(300))
            .andExpect(jsonPath("$.consumedMandays").value(160))
            .andExpect(jsonPath("$.remainingMandays").value(140))
            .andExpect(jsonPath("$.burnPct").value(53))
            .andExpect(jsonPath("$.projectCount").value(2));
    }

    @Test
    void portfolioSummaryWithNoProjectsReturnsZeros() throws Exception {
        when(projects.findByPortfolioIdAndActiveTrue(99L)).thenReturn(List.of());

        mvc.perform(get("/api/v1/man-days/portfolio-summary").param("portfolioId", "99"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.soldMandays").value(0))
            .andExpect(jsonPath("$.consumedMandays").value(0))
            .andExpect(jsonPath("$.burnPct").value(0))
            .andExpect(jsonPath("$.projectCount").value(0));
    }

    @Test
    void portfolioSummaryWithNoBudgetCountsZeroPurchased() throws Exception {
        Project p = project(3L, "Gamma");
        when(projects.findByPortfolioIdAndActiveTrue(5L)).thenReturn(List.of(p));
        when(budgets.findByProjectId(3L)).thenReturn(Optional.empty());
        when(snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(3L))
            .thenReturn(List.of(snapshot(p, 20.0)));

        mvc.perform(get("/api/v1/man-days/portfolio-summary").param("portfolioId", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.soldMandays").value(0))
            .andExpect(jsonPath("$.consumedMandays").value(20))
            .andExpect(jsonPath("$.burnPct").value(0));
    }

    @Test
    void portfolioSummaryWithNoSnapshotsCountsZeroBurned() throws Exception {
        Project p = project(4L, "Delta");
        when(projects.findByPortfolioIdAndActiveTrue(6L)).thenReturn(List.of(p));
        when(budgets.findByProjectId(4L)).thenReturn(Optional.of(budget(p, 150.0)));
        when(snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(4L)).thenReturn(List.of());

        mvc.perform(get("/api/v1/man-days/portfolio-summary").param("portfolioId", "6"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.soldMandays").value(150))
            .andExpect(jsonPath("$.consumedMandays").value(0))
            .andExpect(jsonPath("$.remainingMandays").value(150))
            .andExpect(jsonPath("$.burnPct").value(0));
    }

    // ── /man-days (list) ───────────────────────────────────────────────────────

    @Test
    void listReturnsProjectsWithBurnStatus() throws Exception {
        Project p = project(1L, "Proj A");
        when(projects.findByActiveTrue()).thenReturn(List.of(p));
        when(budgets.findByProjectId(1L)).thenReturn(Optional.of(budget(p, 100.0)));
        when(snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(1L))
            .thenReturn(List.of(snapshot(p, 50.0)));

        mvc.perform(get("/api/v1/man-days"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Proj A"))
            .andExpect(jsonPath("$[0].total").value(100))
            .andExpect(jsonPath("$[0].burned").value(50))
            .andExpect(jsonPath("$[0].st").value("healthy"));
    }

    @Test
    void listMarksCriticalWhenBurnOver90Pct() throws Exception {
        Project p = project(2L, "At Risk");
        when(projects.findByActiveTrue()).thenReturn(List.of(p));
        when(budgets.findByProjectId(2L)).thenReturn(Optional.of(budget(p, 100.0)));
        when(snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(anyLong()))
            .thenReturn(List.of(snapshot(p, 95.0)));

        mvc.perform(get("/api/v1/man-days"))
            .andExpect(jsonPath("$[0].st").value("critical"));
    }
}
