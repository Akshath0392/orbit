package com.orbit.service.ai;

import com.orbit.domain.alert.Alert;
import com.orbit.domain.capacity.Developer;
import com.orbit.repository.AlertRepository;
import com.orbit.repository.DeveloperRepository;
import com.orbit.repository.JiraIssueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CopilotContextServiceTest {

    private final AlertRepository alerts = mock(AlertRepository.class);
    private final JiraIssueRepository issues = mock(JiraIssueRepository.class);
    private final DeveloperRepository developers = mock(DeveloperRepository.class);
    private final CopilotContextService svc = new CopilotContextService(alerts, issues, developers);

    private Alert alert(String severity, String title, String sourceAgent) {
        Alert a = new Alert();
        a.setSeverity(severity);
        a.setTitle(title);
        a.setSourceAgent(sourceAgent);
        a.setStatus("OPEN");
        return a;
    }

    private Developer dev(int util, boolean onLeave) {
        Developer d = new Developer();
        ReflectionTestUtils.setField(d, "utilization", util);
        ReflectionTestUtils.setField(d, "onLeave", onLeave);
        return d;
    }

    @Test
    void digestSummarisesAlertsCrsBugsAndCapacity() {
        when(alerts.countBySeverityAndStatus("critical", "OPEN")).thenReturn(2L);
        when(alerts.countBySeverityAndStatus("risk", "OPEN")).thenReturn(1L);
        when(alerts.findTop5ByStatusOrderByCreatedAtDesc("OPEN")).thenReturn(List.of(
            alert("critical", "P0 SLA breach on ACME-12", "escalation"),
            alert("risk", "CR aging in Staging", "reminder")
        ));
        // 3 open CRs across two stages
        when(issues.findOpenAmCrRows(5L)).thenReturn(List.of(
            row("Staging"), row("Staging"), row("Hold")
        ));
        // prod bugs: portfolio 5 has P0×1, P1×2; portfolio 9 excluded
        when(issues.countOpenProdBugsByPortfolioAndSeverity()).thenReturn(List.of(
            new Object[]{5L, "P0", 1L}, new Object[]{5L, "P1", 2L}, new Object[]{9L, "P0", 4L}
        ));
        when(developers.findAllByOrderByUtilizationDesc()).thenReturn(List.of(
            dev(92, false), dev(88, true), dev(40, false)
        ));

        String digest = svc.buildDigest(5L);

        assertThat(digest).contains("OPEN ALERTS: 2 critical, 1 risk");
        assertThat(digest).contains("P0 SLA breach on ACME-12");
        assertThat(digest).contains("via escalation");
        assertThat(digest).contains("OPEN CRs: 3");
        assertThat(digest).contains("Staging 2").contains("Hold 1");
        assertThat(digest).contains("OPEN PROD BUGS: 3");
        assertThat(digest).contains("P0 1").contains("P1 2");
        assertThat(digest).doesNotContain("P0 4"); // portfolio 9 filtered out
        assertThat(digest).contains("CAPACITY: 3 devs, 2 over 85% util");
        assertThat(digest).contains("peak 92%");
        assertThat(digest).contains("1 on leave");
    }

    @Test
    void emptyStateReadsGracefully() {
        when(alerts.countBySeverityAndStatus(any(), any())).thenReturn(0L);
        when(alerts.findTop5ByStatusOrderByCreatedAtDesc("OPEN")).thenReturn(List.of());
        when(issues.findOpenAmCrRows(null)).thenReturn(List.of());
        when(issues.countOpenProdBugsByPortfolioAndSeverity()).thenReturn(List.of());
        when(developers.findAllByOrderByUtilizationDesc()).thenReturn(List.of());

        String digest = svc.buildDigest(null);

        assertThat(digest).contains("OPEN ALERTS: 0 critical, 0 risk");
        assertThat(digest).contains("(no open alerts)");
        assertThat(digest).contains("OPEN CRs: 0");
        assertThat(digest).contains("OPEN PROD BUGS: 0");
        assertThat(digest).contains("CAPACITY: 0 devs");
    }

    // findOpenAmCrRows tuple shape: [client, stage, assignee, opsModel, createdAt, sm, pjm]
    private Object[] row(String stage) {
        return new Object[]{"ACME", stage, "Dev A", "bau", LocalDateTime.now(), null, null};
    }
}
