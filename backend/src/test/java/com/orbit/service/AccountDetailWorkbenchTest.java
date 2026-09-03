package com.orbit.service;

import com.orbit.domain.client.Client;
import com.orbit.domain.client.Project;
import com.orbit.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The Workbench tab's three mock cards map from real signals:
 * resolved-in-7d counts, release-calendar window, UAT cycle statuses,
 * Hold/awaited stages and open alerts.
 */
class AccountDetailWorkbenchTest {

    private final JiraIssueRepository issues = mock(JiraIssueRepository.class);
    private final ProjectReleaseRepository releases = mock(ProjectReleaseRepository.class);
    private final AlertRepository alerts = mock(AlertRepository.class);
    private final UatCycleRepository uatCycles = mock(UatCycleRepository.class);

    private final AccountDetailService service = new AccountDetailService(
        mock(ProjectRepository.class), issues,
        mock(ManDayBudgetRepository.class), mock(ManDaySnapshotRepository.class),
        mock(ProjectTeamRepository.class), mock(ProjectRiskRepository.class),
        releases, mock(ProjectWinRepository.class),
        mock(GovernanceMeetingRepository.class), mock(ProjectHealthService.class),
        mock(StageSlaTargetRepository.class),
        mock(com.orbit.service.dashboard.RiskScoringService.class),
        alerts, uatCycles);

    @Test
    @SuppressWarnings("unchecked")
    void workbenchCardsMapFromLiveSignals() {
        Project p = new Project();
        ReflectionTestUtils.setField(p, "id", 42L);
        Client c = new Client();
        ReflectionTestUtils.setField(c, "id", 7L);
        p.setClient(c);

        when(issues.countResolvedSinceByType(eq(42L), any()))
            .thenReturn(List.<Object[]>of(new Object[]{"CR", 3L}, new Object[]{"PROD_BUG", 2L}, new Object[]{"UAT_BUG", 2L}));
        when(issues.countOpenCrsByStageLike(42L, "hold")).thenReturn(2L);
        when(issues.countOpenCrsByStageLike(42L, "%awaited%")).thenReturn(1L);
        when(issues.countOpenCrsByStageLike(42L, "%client approval%")).thenReturn(4L);
        when(releases.findByProjectIdAndReleaseDateBetweenOrderByReleaseDateAsc(eq(42L), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(mock(com.orbit.domain.account.ProjectRelease.class)));
        when(uatCycles.countByIssueClientIdAndSignOffStatus(7L, "SIGNED_OFF")).thenReturn(5L);
        when(uatCycles.countByIssueClientIdAndSignOffStatus(7L, "PENDING")).thenReturn(2L);
        when(alerts.countByProjectIdAndStatus(42L, "OPEN")).thenReturn(1L);

        Map<String, Object> wb = ReflectionTestUtils.invokeMethod(service, "buildWorkbench", p);

        Map<String, Object> thisWeek = (Map<String, Object>) wb.get("thisWeek");
        assertEquals(3L, thisWeek.get("crsClosed"));
        assertEquals(4L, thisWeek.get("bugsFixed"));      // PROD_BUG + UAT_BUG
        assertEquals(5L, thisWeek.get("uatSignOffs"));

        Map<String, Object> nextWeek = (Map<String, Object>) wb.get("nextWeek");
        assertEquals(1, nextWeek.get("goLives"));
        assertEquals(2L, nextWeek.get("uatCycles"));
        assertEquals(4L, nextWeek.get("signOffsDue"));

        Map<String, Object> attention = (Map<String, Object>) wb.get("attention");
        assertEquals(2L, attention.get("blocked"));
        assertEquals(1L, attention.get("awaitingClient"));
        assertEquals(1L, attention.get("escalations"));
    }
}
