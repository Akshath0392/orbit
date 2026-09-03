package com.orbit.controller;

import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that filter query parameters (clientId, severity, slaStatus, stage)
 * are wired through from the controller into the repository layer.
 *
 * The filter dropdowns on the frontend were broken because they weren't passing
 * these params; this test locks in the contract end-to-end (HTTP -> controller -> repo).
 */
@WebMvcTest(
    value = BugController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class BugControllerTest {

    @Autowired MockMvc mvc;
    @MockBean JwtService jwtService;
    @MockBean JiraIssueRepository issues;
    @MockBean ProjectRepository projects;

    // ── Production bugs: filter params ────────────────────────────────────────

    @Test
    void prodBugsPassesClientIdSeverityAndSlaStatusToRepository() throws Exception {
        when(issues.findProdBugs(any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/bugs/prod")
                .param("clientId",  "42")
                .param("severity",  "P0")
                .param("slaStatus", "Breached"))
            .andExpect(status().isOk());

        ArgumentCaptor<Long> clientIdCap   = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> sevCap      = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> slaCap      = ArgumentCaptor.forClass(String.class);
        verify(issues).findProdBugs(clientIdCap.capture(), sevCap.capture(), slaCap.capture(), any(Pageable.class));

        org.junit.jupiter.api.Assertions.assertEquals(42L, clientIdCap.getValue());
        org.junit.jupiter.api.Assertions.assertEquals("P0", sevCap.getValue());
        org.junit.jupiter.api.Assertions.assertEquals("Breached", slaCap.getValue());
    }

    @Test
    void prodBugsWithoutFiltersPassesNullsToRepository() throws Exception {
        when(issues.findProdBugs(any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/bugs/prod"))
            .andExpect(status().isOk());

        verify(issues).findProdBugs(isNull(), isNull(), isNull(), any(Pageable.class));
    }

    // ── UAT bugs: filter params ───────────────────────────────────────────────

    @Test
    void uatBugsPassesClientIdAndStageToRepository() throws Exception {
        when(issues.findUatBugs(any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/bugs/uat")
                .param("clientId", "7")
                .param("stage",    "Retesting"))
            .andExpect(status().isOk());

        verify(issues).findUatBugs(eq(7L), eq("Retesting"), any(Pageable.class));
    }

    @Test
    void uatBugsWithoutFiltersPassesNulls() throws Exception {
        when(issues.findUatBugs(any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/bugs/uat"))
            .andExpect(status().isOk());

        verify(issues).findUatBugs(isNull(), isNull(), any(Pageable.class));
    }

    // ── Prod summary: clientId wired ──────────────────────────────────────────

    @Test
    void prodSummaryPassesClientIdAndProdTypeToCountQueries() throws Exception {
        stubSummaryRepoMethods();

        mvc.perform(get("/api/v1/bugs/prod/summary").param("clientId", "5"))
            .andExpect(status().isOk());

        verify(issues).countOpenByClientTypeAndSeverityIn(eq(5L), eq("PROD_BUG"), eq(List.of("P0")));
        verify(issues).countOpenByClientTypeAndSeverityIn(eq(5L), eq("PROD_BUG"), eq(List.of("P1")));
        verify(issues).countOpenBySlaStatusAndType(eq(5L), eq("PROD_BUG"), eq("Breached"));
        verify(issues).countOpenBySlaStatusAndType(eq(5L), eq("PROD_BUG"), eq("At risk"));
        verify(issues).countOpenReopenedByClientAndType(eq(5L), eq("PROD_BUG"));
        verify(issues).countOpenUnassignedByClientAndType(eq(5L), eq("PROD_BUG"));
    }

    @Test
    void prodSummaryWithNoClientCountsAcrossAllClients() throws Exception {
        // Previously the controller passed clientId=0 (no match), making counts always 0.
        // Verify it now passes null so the repo counts globally.
        stubSummaryRepoMethods();

        mvc.perform(get("/api/v1/bugs/prod/summary"))
            .andExpect(status().isOk());

        verify(issues).countOpenByClientTypeAndSeverityIn(isNull(), eq("PROD_BUG"), eq(List.of("P0")));
        verify(issues).countOpenByClientTypeAndSeverityIn(isNull(), eq("PROD_BUG"), eq(List.of("P1")));
        verify(issues).countOpenReopenedByClientAndType(isNull(), eq("PROD_BUG"));
        verify(issues).countOpenUnassignedByClientAndType(isNull(), eq("PROD_BUG"));
    }

    // ── UAT summary: same shape but UAT_BUG type ──────────────────────────────

    @Test
    void uatSummaryPassesClientIdAndUatTypeToCountQueries() throws Exception {
        stubSummaryRepoMethods();

        mvc.perform(get("/api/v1/bugs/uat/summary").param("clientId", "7"))
            .andExpect(status().isOk());

        verify(issues).countOpenByClientTypeAndSeverityIn(eq(7L), eq("UAT_BUG"), eq(List.of("P0")));
        verify(issues).countOpenByClientTypeAndSeverityIn(eq(7L), eq("UAT_BUG"), eq(List.of("P1")));
        verify(issues).countOpenBySlaStatusAndType(eq(7L), eq("UAT_BUG"), eq("Breached"));
        verify(issues).countOpenBySlaStatusAndType(eq(7L), eq("UAT_BUG"), eq("At risk"));
        verify(issues).countOpenReopenedByClientAndType(eq(7L), eq("UAT_BUG"));
        verify(issues).countOpenUnassignedByClientAndType(eq(7L), eq("UAT_BUG"));
    }

    @Test
    void uatSummaryReturnsAllExpectedFields() throws Exception {
        stubSummaryRepoMethods();
        when(issues.countOpenByClientTypeAndSeverityIn(any(), eq("UAT_BUG"), eq(List.of("P0")))).thenReturn(3L);
        when(issues.countOpenByClientTypeAndSeverityIn(any(), eq("UAT_BUG"), eq(List.of("P1")))).thenReturn(8L);
        when(issues.countOpenReopenedByClientAndType(any(), eq("UAT_BUG"))).thenReturn(2L);
        when(issues.countOpenUnassignedByClientAndType(any(), eq("UAT_BUG"))).thenReturn(5L);

        mvc.perform(get("/api/v1/bugs/uat/summary"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.p0Open").value(3))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.p1Open").value(8))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.reopened").value(2))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.unassigned").value(5))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.slaBreached").exists())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.slaAtRisk").exists());
    }

    @Test
    void prodAndUatSummariesAreIsolated() throws Exception {
        stubSummaryRepoMethods();

        mvc.perform(get("/api/v1/bugs/prod/summary").param("clientId", "1")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/bugs/uat/summary").param("clientId", "1")).andExpect(status().isOk());

        // Prod call must never request UAT counts and vice versa
        verify(issues).countOpenByClientTypeAndSeverityIn(eq(1L), eq("PROD_BUG"), eq(List.of("P0")));
        verify(issues).countOpenByClientTypeAndSeverityIn(eq(1L), eq("UAT_BUG"),  eq(List.of("P0")));
        verify(issues, org.mockito.Mockito.never())
            .countOpenByClientTypeAndSeverityIn(any(), eq("CR"), any());
    }

    private void stubSummaryRepoMethods() {
        when(issues.countOpenByClientTypeAndSeverityIn(any(), any(), any())).thenReturn(0L);
        when(issues.countOpenBySlaStatusAndType(any(), any(), any())).thenReturn(0L);
        when(issues.countOpenReopenedByClientAndType(any(), any())).thenReturn(0L);
        when(issues.countOpenUnassignedByClientAndType(any(), any())).thenReturn(0L);
    }

    // ── Helper assertion: response is a valid Page envelope ───────────────────

    @Test
    void prodBugsReturnsContentEnvelopeEvenWhenEmpty() throws Exception {
        when(issues.findProdBugs(any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/bugs/prod"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content").isArray());
    }

    // ── Smoke test for response mapping when bugs exist ───────────────────────

    @Test
    void prodBugsResponseIncludesKeyAndSeverity() throws Exception {
        JiraIssue i = new JiraIssue();
        i.setIssueKey("NX-100");
        i.setSummary("Login fails");
        i.setSeverity("P0");
        i.setSlaStatus("Breached");
        i.setIssueType("PROD_BUG");
        when(issues.findProdBugs(any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(i)));

        mvc.perform(get("/api/v1/bugs/prod"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].key").value("NX-100"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].sev").value("P0"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].slaS").value("Breached"));
    }
}
