package com.orbit.service.slack;

import com.orbit.domain.alert.Alert;
import com.orbit.domain.capacity.Developer;
import com.orbit.domain.capacity.ManDaySnapshot;
import com.orbit.domain.client.Project;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.domain.report.GeneratedReport;
import com.orbit.integration.slack.SlackResponseRenderer;
import com.orbit.domain.client.AppUser;
import com.orbit.integration.slack.SlackInteractionRouter.Surface;
import com.orbit.repository.AlertRepository;
import com.orbit.repository.DeveloperRepository;
import com.orbit.repository.GeneratedReportRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.ManDaySnapshotRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.service.agent.AgentInvocationResult;
import com.orbit.service.agent.AgentInvocationService;
import com.orbit.service.slack.IntentResolver.ResolvedIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SlackToolExecutorTest {

    AlertRepository alerts;
    JiraIssueRepository jira;
    SlackResponseRenderer renderer = new SlackResponseRenderer();
    AgentInvocationService invocations;
    ProjectRepository projectRepo;
    DeveloperRepository developerRepo;
    ManDaySnapshotRepository snapshotRepo;
    GeneratedReportRepository reportRepo;
    SlackToolExecutor exec;

    @BeforeEach
    void setUp() {
        alerts = mock(AlertRepository.class);
        jira = mock(JiraIssueRepository.class);
        invocations = mock(AgentInvocationService.class);
        projectRepo = mock(ProjectRepository.class);
        developerRepo = mock(DeveloperRepository.class);
        snapshotRepo = mock(ManDaySnapshotRepository.class);
        reportRepo = mock(GeneratedReportRepository.class);
        when(alerts.findFiltered(any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(jira.findCrs(any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(jira.findProdBugs(any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(reportRepo.findFiltered(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(projectRepo.findByActiveTrue()).thenReturn(List.of());
        when(developerRepo.findAllByOrderByUtilizationDesc()).thenReturn(List.of());
        exec = new SlackToolExecutor(alerts, jira, renderer, invocations,
            projectRepo, developerRepo, snapshotRepo, reportRepo);
    }

    private static Alert alert(String sev, String title, String projectName) {
        Alert a = new Alert();
        ReflectionTestUtils.setField(a, "id", 1L);
        a.setSeverity(sev);
        a.setTitle(title);
        a.setStatus("OPEN");
        ReflectionTestUtils.setField(a, "createdAt", LocalDateTime.now().minusMinutes(45));
        if (projectName != null) {
            Project p = new Project();
            p.setName(projectName);
            ReflectionTestUtils.setField(a, "project", p);
        }
        return a;
    }

    private static JiraIssue bug(String sev, String slaStatus) {
        JiraIssue j = new JiraIssue();
        j.setSeverity(sev);
        j.setSlaStatus(slaStatus);
        return j;
    }

    @Test
    void get_alerts_passes_uppercased_severity_to_repository_and_renders() {
        Page<Alert> page = new PageImpl<>(List.of(
            alert("CRITICAL", "SLA breach NX-101", "CRM Core"),
            alert("CRITICAL", "Capacity overload", "Mobile")
        ));
        when(alerts.findFiltered(eq("CRITICAL"), eq("OPEN"), eq((Long) null), any(Pageable.class)))
            .thenReturn(page);

        var blocks = exec.execute(new ResolvedIntent("orbit.get_alerts",
            Map.of("severity", "critical"), "test"));

        ArgumentCaptor<String> sev = ArgumentCaptor.forClass(String.class);
        verify(alerts).findFiltered(sev.capture(), eq("OPEN"), eq((Long) null), any(Pageable.class));
        assertThat(sev.getValue()).isEqualTo("CRITICAL");
        // header + 2 rows + context
        assertThat(blocks).hasSize(4);
        assertThat(blocks.get(0).get("type")).isEqualTo("header");
    }

    @Test
    void get_alerts_with_no_severity_passes_null_filter() {
        when(alerts.findFiltered(eq((String) null), eq("OPEN"), eq((Long) null), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        var blocks = exec.execute(new ResolvedIntent("orbit.get_alerts", Map.of(), "test"));
        verify(alerts).findFiltered(eq((String) null), eq("OPEN"), eq((Long) null), any(Pageable.class));
        // empty-state card = 2 blocks
        assertThat(blocks).hasSize(2);
    }

    @Test
    void get_bugs_counts_by_severity_and_sla_breaches() {
        Page<JiraIssue> page = new PageImpl<>(List.of(
            bug("P0", "Breached"),
            bug("P1", "On track"),
            bug("P2", "Breached"),
            bug("P2", "On track"),
            bug("P3", "On track")
        ));
        when(jira.findProdBugs(any(), any(), any(), any(Pageable.class))).thenReturn(page);

        var blocks = exec.execute(new ResolvedIntent("orbit.get_bugs", Map.of(), "test"));

        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) blocks.get(1).get("text")).get("text");
        assertThat(body).contains("P0 *1*", "P1 *1*", "P2 *2*", "P3 *1*", ":warning: 2 SLA-breached");
    }

    @Test
    void get_crs_without_project_uses_findCrs() {
        JiraIssue cr = new JiraIssue();
        cr.setIssueKey("ORB-42");
        cr.setSummary("Improve onboarding");
        cr.setLifecycleStage("DEV");
        cr.setPriority("P1");
        when(jira.findCrs(any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(cr)));
        var blocks = exec.execute(new ResolvedIntent("orbit.get_crs", Map.of(), "test"));
        verify(jira).findCrs(any(), any(), any(), any(Pageable.class));
        verify(jira, never()).findCrsByProjectIds(any(), any(), any(), any(Pageable.class));
        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) blocks.get(1).get("text")).get("text");
        assertThat(body).contains("ORB-42", "Improve onboarding", "DEV");
    }

    @Test
    void get_crs_with_project_scopes_to_matching_project_ids() {
        Project p = new Project(); p.setName("Apollo");
        ReflectionTestUtils.setField(p, "id", 11L);
        when(projectRepo.findByActiveTrue()).thenReturn(List.of(p));
        when(jira.findCrsByProjectIds(any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        exec.execute(new ResolvedIntent("orbit.get_crs", Map.of("projectName", "apollo"), "test"));
        ArgumentCaptor<List<Long>> idsCap = ArgumentCaptor.forClass(List.class);
        verify(jira).findCrsByProjectIds(idsCap.capture(), any(), any(), any(Pageable.class));
        assertThat(idsCap.getValue()).containsExactly(11L);
    }

    @Test
    void get_crs_with_unknown_project_returns_empty_state() {
        when(projectRepo.findByActiveTrue()).thenReturn(List.of());
        var blocks = exec.execute(new ResolvedIntent("orbit.get_crs",
            Map.of("projectName", "Nonexistent"), "test"));
        verify(jira, never()).findCrsByProjectIds(any(), any(), any(), any(Pageable.class));
        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) blocks.get(1).get("text")).get("text");
        assertThat(body).contains("Nonexistent");
    }

    @Test
    void get_briefing_aggregates_signals_across_alerts_bugs_crs() {
        Alert a = alert("CRITICAL", "x", null);
        when(alerts.findFiltered(eq("CRITICAL"), eq("OPEN"), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(a), Pageable.unpaged(), 3));
        when(alerts.findFiltered(eq("WARNING"), eq("OPEN"), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 2));
        when(jira.findProdBugs(any(), eq("P0"), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 1));
        when(jira.findCrs(any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 5));
        var blocks = exec.execute(new ResolvedIntent("orbit.get_briefing", Map.of(), "test"));
        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) blocks.get(1).get("text")).get("text");
        assertThat(body).contains("3 critical alerts", "1 P0 bug", "2 warning alerts", "5 open change requests");
    }

    @Test
    void get_briefing_all_quiet_when_no_signals() {
        var blocks = exec.execute(new ResolvedIntent("orbit.get_briefing", Map.of(), "test"));
        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) blocks.get(1).get("text")).get("text");
        assertThat(body).contains("All quiet");
    }

    @Test
    void get_forecast_without_project_prompts_for_name() {
        var blocks = exec.execute(new ResolvedIntent("orbit.get_forecast", Map.of(), "test"));
        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) blocks.get(1).get("text")).get("text");
        assertThat(body).contains("which project");
    }

    @Test
    void get_forecast_for_unknown_project_returns_empty_state() {
        var blocks = exec.execute(new ResolvedIntent("orbit.get_forecast",
            Map.of("projectName", "Ghost"), "test"));
        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) blocks.get(1).get("text")).get("text");
        assertThat(body).contains("Ghost");
    }

    @Test
    void get_forecast_renders_burn_pct_and_exhaustion_from_latest_snapshot() {
        Project p = new Project(); p.setName("Apollo");
        ReflectionTestUtils.setField(p, "id", 7L);
        when(projectRepo.findByActiveTrue()).thenReturn(List.of(p));
        ManDaySnapshot s = new ManDaySnapshot();
        ReflectionTestUtils.setField(s, "burnedDays",          new BigDecimal("60"));
        ReflectionTestUtils.setField(s, "remainingDays",       new BigDecimal("40"));
        ReflectionTestUtils.setField(s, "burnRatePerDay",      new BigDecimal("2"));
        ReflectionTestUtils.setField(s, "forecastExhaustion",  LocalDate.of(2026, 7, 15));
        when(snapshotRepo.findTop14ByProjectIdOrderBySnapshotDateDesc(7L)).thenReturn(List.of(s));
        var blocks = exec.execute(new ResolvedIntent("orbit.get_forecast",
            Map.of("projectName", "Apollo"), "test"));
        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) blocks.get(1).get("text")).get("text");
        assertThat(body).contains("Apollo", "60%", "2026-07-15", "20");
    }

    @Test
    void get_capacity_groups_by_team_and_averages_utilisation() {
        Developer a = new Developer(); ReflectionTestUtils.setField(a, "team", "Backend"); ReflectionTestUtils.setField(a, "utilization", 90);
        Developer b = new Developer(); ReflectionTestUtils.setField(b, "team", "Backend"); ReflectionTestUtils.setField(b, "utilization", 70);
        Developer c = new Developer(); ReflectionTestUtils.setField(c, "team", "Mobile");  ReflectionTestUtils.setField(c, "utilization", 50);
        when(developerRepo.findAllByOrderByUtilizationDesc()).thenReturn(List.of(a, b, c));
        var blocks = exec.execute(new ResolvedIntent("orbit.get_capacity", Map.of(), "test"));
        String full = blocks.stream().map(Object::toString).reduce("", (x, y) -> x + " " + y);
        assertThat(full).contains("Backend", "80%", "Mobile", "50%");
    }

    @Test
    void get_capacity_empty_when_no_developers() {
        var blocks = exec.execute(new ResolvedIntent("orbit.get_capacity", Map.of(), "test"));
        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) blocks.get(1).get("text")).get("text");
        assertThat(body).contains("No developers");
    }

    @Test
    void get_report_status_lists_latest_reports() {
        GeneratedReport r = new GeneratedReport();
        ReflectionTestUtils.setField(r, "type",        "Weekly delivery");
        ReflectionTestUtils.setField(r, "status",      "COMPLETED");
        ReflectionTestUtils.setField(r, "generatedBy", "akshath@orbit.io");
        ReflectionTestUtils.setField(r, "generatedAt", LocalDateTime.of(2026, 6, 20, 10, 0));
        when(reportRepo.findFiltered(any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(r)));
        var blocks = exec.execute(new ResolvedIntent("orbit.get_report_status", Map.of(), "test"));
        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) blocks.get(1).get("text")).get("text");
        assertThat(body).contains("Weekly delivery", "COMPLETED", "akshath@orbit.io", "2026-06-20");
    }

    @Test
    void null_intent_returns_help_card() {
        var blocks = exec.execute(null);
        assertThat(blocks).hasSize(2);
        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) blocks.get(1).get("text")).get("text");
        assertThat(body).contains("didn't understand", "alerts critical", "bugs p0");
    }

    @Test
    void run_forecast_invokes_native_agent_with_slack_slash_source() {
        AppUser u = new AppUser(); u.setEmail("p@orbit.io");
        when(invocations.invoke(eq(u), eq("forecast.manday"), any(), eq("SLACK_SLASH")))
            .thenReturn(AgentInvocationResult.completed(42L, "forecast.manday", "forecast.manday:ok", Map.of()));

        var blocks = exec.execute(new ResolvedIntent("orbit.run_forecast", Map.of(), ""), u, Surface.SLASH);

        verify(invocations).invoke(eq(u), eq("forecast.manday"), any(), eq("SLACK_SLASH"));
        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) blocks.get(1).get("text")).get("text");
        assertThat(body).contains("Run #42 started");
    }

    @Test
    void run_briefing_via_mention_tags_invocation_source_slack_mention() {
        AppUser u = new AppUser(); u.setEmail("p@orbit.io");
        when(invocations.invoke(any(), eq("briefing.delivery"), any(), eq("SLACK_MENTION")))
            .thenReturn(AgentInvocationResult.completed(7L, "briefing.delivery", "ok", Map.of()));

        exec.execute(new ResolvedIntent("orbit.run_briefing", Map.of(), ""), u, Surface.MENTION);

        verify(invocations).invoke(eq(u), eq("briefing.delivery"), any(), eq("SLACK_MENTION"));
    }

    @Test
    void run_briefing_via_dm_tags_invocation_source_slack_dm() {
        AppUser u = new AppUser(); u.setEmail("p@orbit.io");
        when(invocations.invoke(any(), eq("briefing.delivery"), any(), eq("SLACK_DM")))
            .thenReturn(AgentInvocationResult.completed(8L, "briefing.delivery", "ok", Map.of()));

        exec.execute(new ResolvedIntent("orbit.run_briefing", Map.of(), ""), u, Surface.DM);

        verify(invocations).invoke(eq(u), eq("briefing.delivery"), any(), eq("SLACK_DM"));
    }

    @Test
    void run_report_without_reportId_explains_instead_of_invoking() {
        AppUser u = new AppUser(); u.setEmail("p@orbit.io");
        var blocks = exec.execute(new ResolvedIntent("orbit.run_report", Map.of(), ""), u, Surface.SLASH);
        verifyNoInteractions(invocations);
        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) blocks.get(1).get("text")).get("text");
        assertThat(body).contains("Orbit UI", "reportId");
    }

    @Test
    void run_report_with_reportId_invokes_report_draft() {
        AppUser u = new AppUser(); u.setEmail("p@orbit.io");
        when(invocations.invoke(eq(u), eq("report.draft"), any(), eq("SLACK_SLASH")))
            .thenReturn(AgentInvocationResult.completed(99L, "report.draft", "report.draft:ok", Map.of()));

        exec.execute(new ResolvedIntent("orbit.run_report",
            Map.of("reportId", 123), ""), u, Surface.SLASH);

        verify(invocations).invoke(eq(u), eq("report.draft"), any(), eq("SLACK_SLASH"));
    }

    @Test
    void run_agent_failure_renders_error_card_not_throws() {
        AppUser u = new AppUser(); u.setEmail("p@orbit.io");
        when(invocations.invoke(any(), eq("forecast.manday"), any(), any()))
            .thenReturn(AgentInvocationResult.failed(5L, "forecast.manday", "boom"));
        var blocks = exec.execute(new ResolvedIntent("orbit.run_forecast", Map.of(), ""), u, Surface.SLASH);
        @SuppressWarnings("unchecked")
        String body = (String) ((Map<String, Object>) blocks.get(1).get("text")).get("text");
        assertThat(body).contains("Run #5 failed");
    }

    @Test
    void fallback_text_uses_tool_display_name() {
        assertThat(exec.fallbackText(new ResolvedIntent("orbit.get_alerts", Map.of(), ""))).isEqualTo("Orbit · Alerts");
        assertThat(exec.fallbackText(null)).isEqualTo("Orbit didn't understand that.");
    }
}
