package com.orbit.controller;

import com.orbit.domain.client.Project;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.IssueMilestoneRepository;
import com.orbit.repository.IssueNoteRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = CrController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class CrControllerTest {

    @Autowired MockMvc mvc;
    @MockBean JwtService jwtService;
    @MockBean JiraIssueRepository issues;
    @MockBean IssueMilestoneRepository milestones;
    @MockBean IssueNoteRepository notes;
    @MockBean com.orbit.repository.ProjectRepository projects;
    @MockBean com.orbit.repository.LifecycleMappingRepository lifecycle;

    // ── helpers ───────────────────────────────────────────────────────────────

    private JiraIssue cr(String key, String summary, String stage, String jiraStatus,
                          String priority, String owner) {
        JiraIssue i = new JiraIssue();
        i.setIssueKey(key);
        i.setSummary(summary);
        i.setIssueType("CR");
        i.setLifecycleStage(stage);
        i.setJiraStatus(jiraStatus);
        i.setPriority(priority);
        i.setAssigneeName(owner);
        i.setCreatedAt(LocalDateTime.now().minusDays(5));
        return i;
    }

    // ── /cr/export ─────────────────────────────────────────────────────────────

    @Test
    void exportReturnsCsvContentType() throws Exception {
        when(issues.findCrsFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/cr/export"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    void exportHasContentDispositionHeader() throws Exception {
        when(issues.findCrsFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/cr/export"))
            .andExpect(header().string("Content-Disposition", containsString("attachment")))
            .andExpect(header().string("Content-Disposition", containsString("cr-export.csv")));
    }

    @Test
    void exportAlwaysIncludesHeaderRow() throws Exception {
        when(issues.findCrsFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/cr/export"))
            .andExpect(content().string(containsString("CR,Client,Description,Status,Stage,POD,SM,PjM,Type,Aging")));
    }

    @Test
    void exportEmptyResultsReturnsOnlyHeaderRow() throws Exception {
        when(issues.findCrsFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        String body = mvc.perform(get("/api/v1/cr/export"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Only the header row — no data rows
        String[] lines = body.strip().split("\n");
        org.junit.jupiter.api.Assertions.assertEquals(1, lines.length);
    }

    @Test
    void exportRowContainsAllFields() throws Exception {
        JiraIssue issue = cr("NX-101", "Add export feature", "In dev", "In Progress", "High", "rajan.m");
        when(issues.findCrsFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(issue)));

        String body = mvc.perform(get("/api/v1/cr/export"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String[] lines = body.strip().split("\n");
        org.junit.jupiter.api.Assertions.assertEquals(2, lines.length);

        String dataRow = lines[1];
        org.junit.jupiter.api.Assertions.assertTrue(dataRow.contains("NX-101"));
        org.junit.jupiter.api.Assertions.assertTrue(dataRow.contains("Add export feature"));
        org.junit.jupiter.api.Assertions.assertTrue(dataRow.contains("In dev"));
        org.junit.jupiter.api.Assertions.assertTrue(dataRow.contains("In Progress"));
    }

    @Test
    void exportMultipleRowsProducedCorrectly() throws Exception {
        List<JiraIssue> crs = List.of(
            cr("NX-101", "First CR",  "In dev",       "In Progress",    "High",   "alice"),
            cr("NX-102", "Second CR", "In QA",        "Ready for QA",   "Medium", "bob"),
            cr("NX-103", "Third CR",  "BRD awaited",  "Backlog",        "Low",    "—")
        );
        when(issues.findCrsFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(crs));

        String body = mvc.perform(get("/api/v1/cr/export"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String[] lines = body.strip().split("\n");
        org.junit.jupiter.api.Assertions.assertEquals(4, lines.length); // header + 3 data rows
    }

    @Test
    void exportEscapesCommasInSummary() throws Exception {
        JiraIssue issue = cr("NX-200", "Fix login, logout, and session", "In dev", "In Progress", "High", "alice");
        when(issues.findCrsFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(issue)));

        String body = mvc.perform(get("/api/v1/cr/export"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(
            body.contains("\"Fix login, logout, and session\""),
            "Summary with commas must be quoted"
        );
    }

    @Test
    void exportEscapesDoubleQuotesInSummary() throws Exception {
        JiraIssue issue = cr("NX-201", "Fix \"null\" pointer", "In dev", "In Progress", "Medium", "alice");
        when(issues.findCrsFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(issue)));

        String body = mvc.perform(get("/api/v1/cr/export"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(
            body.contains("\"Fix \"\"null\"\" pointer\""),
            "Embedded quotes must be doubled"
        );
    }

    @Test
    void exportPassesStageFilterToRepository() throws Exception {
        when(issues.findCrsFiltered(isNull(), eq("In dev"), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(cr("NX-301", "Filtered", "In dev", "In Progress", "High", "alice"))));

        String body = mvc.perform(get("/api/v1/cr/export").param("stage", "In dev"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(body.contains("NX-301"));
    }

    @Test
    void exportPassesSearchFilterToRepository() throws Exception {
        when(issues.findCrsFiltered(isNull(), isNull(), any(), any(), any(), any(), eq("%auth%"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(cr("NX-401", "Auth bug", "In dev", "In Progress", "High", "alice"))));

        String body = mvc.perform(get("/api/v1/cr/export").param("search", "auth"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(body.contains("NX-401"));
    }

    @Test
    void exportNullAssigneeRendersAsDash() throws Exception {
        JiraIssue issue = cr("NX-501", "Unassigned CR", "BRD awaited", "Backlog", "Medium", null);
        when(issues.findCrsFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(issue)));

        String body = mvc.perform(get("/api/v1/cr/export"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        org.junit.jupiter.api.Assertions.assertTrue(body.contains("—"));
    }

    // ── /cr (list) ─────────────────────────────────────────────────────────────

    @Test
    void listReturnsPaginatedJson() throws Exception {
        JiraIssue issue = cr("NX-001", "Sample CR", "In dev", "In Progress", "High", "alice");
        when(issues.findCrsFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(issue)));

        mvc.perform(get("/api/v1/cr"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].key").value("NX-001"))
            .andExpect(jsonPath("$.content[0].stage").value("In dev"))
            .andExpect(jsonPath("$.content[0].jiraStatus").value("In Progress"));
    }

    @Test
    void listHoldStageProducesRiskCritical() throws Exception {
        JiraIssue issue = cr("NX-002", "Blocked CR", "Hold", "Blocked", "High", "alice");
        when(issues.findCrsFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(issue)));

        mvc.perform(get("/api/v1/cr"))
            .andExpect(jsonPath("$.content[0].risk").value("critical"));
    }

    @Test
    void detailReturnsNotFoundForUnknownKey() throws Exception {
        when(issues.findByIssueKey("UNKNOWN-999")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/cr/UNKNOWN-999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void detailReturnsIssueWithMilestonesAndNotes() throws Exception {
        JiraIssue issue = cr("NX-003", "Detail test", "In QA", "Ready for QA", "Medium", "bob");
        when(issues.findByIssueKey("NX-003")).thenReturn(Optional.of(issue));
        when(milestones.findByIssueId(any())).thenReturn(List.of());
        when(notes.findByIssueIdOrderByCreatedAtDesc(any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/cr/NX-003"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value("NX-003"))
            .andExpect(jsonPath("$.milestones").isArray())
            .andExpect(jsonPath("$.notes").isArray());
    }

    // ── portfolio filter ───────────────────────────────────────────────────────

    @Test
    void listByPortfolioIdFiltersThroughThePortfolioJoin() throws Exception {
        when(issues.findCrsFiltered(isNull(), isNull(), eq(7L), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(cr("NX-010", "Portfolio CR", "In dev", "In Progress", "High", "alice"))));

        mvc.perform(get("/api/v1/cr").param("portfolioId", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].key").value("NX-010"));
    }

    @Test
    void listByPortfolioIdWithNoProjectsReturnsEmpty() throws Exception {
        when(issues.findCrsFiltered(isNull(), isNull(), eq(99L), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/cr").param("portfolioId", "99"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void stageSummaryByPortfolioIdUsesProjectScopedQuery() throws Exception {
        Project p = new Project(); ReflectionTestUtils.setField(p, "id", 11L);
        when(projects.findByPortfolioIdAndActiveTrue(3L)).thenReturn(List.of(p));
        when(issues.countCrsByStageForProjects(List.of(11L)))
            .thenReturn(List.of(new Object[]{"In dev", 4L}, new Object[]{"In QA", 2L}));

        mvc.perform(get("/api/v1/cr/stage-summary").param("portfolioId", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$['In dev']").value(4));
    }

    // ── mock filter set + row fields ──────────────────────────

    @Test
    void typeFilterReachesRepositoryAsOpsModelLike() throws Exception {
        when(issues.findCrsFiltered(isNull(), isNull(), isNull(), isNull(), isNull(), eq("%launch%"), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/cr").param("type", "LAUNCH"))
            .andExpect(status().isOk());
        org.mockito.Mockito.verify(issues).findCrsFiltered(isNull(), isNull(), isNull(), isNull(), isNull(),
            eq("%launch%"), isNull(), any(Pageable.class));
    }

    @Test
    void smAndPjmFiltersPassThroughVerbatim() throws Exception {
        when(issues.findCrsFiltered(isNull(), isNull(), isNull(), eq("Arjun Rao"), eq("Sanjay Iyer"), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/cr").param("sm", "Arjun Rao").param("pjm", "Sanjay Iyer"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void filterOptionsListsDistinctOwners() throws Exception {
        when(issues.findDistinctCrSmOwners()).thenReturn(List.of("Arjun Rao"));
        when(issues.findDistinctCrPjmOwners()).thenReturn(List.of("Sanjay Iyer", "Preeti M"));

        mvc.perform(get("/api/v1/cr/filter-options"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sms[0]").value("Arjun Rao"))
            .andExpect(jsonPath("$.pjms.length()").value(2));
    }

    @Test
    void rowsCarryPodSmPjmAndType() throws Exception {
        JiraIssue i = cr("NX-201", "Owner columns", "In dev", "In Progress", "High", "alice");
        i.setSmOwner("Arjun Rao");
        i.setPjmOwner("Sanjay Iyer");
        Project p = new Project(); p.setOpsModel("launch+bau");
        com.orbit.domain.client.Portfolio pf = new com.orbit.domain.client.Portfolio();
        pf.setName("Collections");
        p.setPortfolio(pf);
        i.setProject(p);
        when(issues.findCrsFiltered(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(i)));

        mvc.perform(get("/api/v1/cr"))
            .andExpect(jsonPath("$.content[0].pod").value("Collections"))
            .andExpect(jsonPath("$.content[0].sm").value("Arjun Rao"))
            .andExpect(jsonPath("$.content[0].pjm").value("Sanjay Iyer"))
            .andExpect(jsonPath("$.content[0].type").value("LAUNCH+BAU"));
    }
}
