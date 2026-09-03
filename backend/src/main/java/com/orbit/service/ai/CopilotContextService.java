package com.orbit.service.ai;

import com.orbit.domain.alert.Alert;
import com.orbit.domain.capacity.Developer;
import com.orbit.repository.AlertRepository;
import com.orbit.repository.DeveloperRepository;
import com.orbit.repository.JiraIssueRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a compact, portfolio-scoped snapshot of current delivery state to
 * ground the copilot's LLM call. Uses only cheap reads (indexed counts + one
 * open-CR tuple scan) so it's safe in the request path. Heavy per-project risk
 * scoring is intentionally out of scope here — open alerts already surface the
 * critical items.
 */
@Service
public class CopilotContextService {

    private static final int MAX_ALERTS = 5;
    private static final int OVERLOAD_UTIL = 85;

    private final AlertRepository alerts;
    private final JiraIssueRepository issues;
    private final DeveloperRepository developers;

    public CopilotContextService(AlertRepository alerts, JiraIssueRepository issues,
                                 DeveloperRepository developers) {
        this.alerts = alerts;
        this.issues = issues;
        this.developers = developers;
    }

    /** portfolioId may be null → whole-book digest. */
    public String buildDigest(Long portfolioId) {
        StringBuilder sb = new StringBuilder();
        sb.append(alertsSection());
        sb.append(crsSection(portfolioId));
        sb.append(prodBugsSection(portfolioId));
        sb.append(capacitySection());
        return sb.toString().trim();
    }

    private String alertsSection() {
        long critical = alerts.countBySeverityAndStatus("critical", "OPEN");
        long risk     = alerts.countBySeverityAndStatus("risk", "OPEN");
        List<Alert> top = alerts.findTop5ByStatusOrderByCreatedAtDesc("OPEN");
        StringBuilder sb = new StringBuilder();
        sb.append("OPEN ALERTS: ").append(critical).append(" critical, ").append(risk).append(" risk.\n");
        if (top.isEmpty()) {
            sb.append("  (no open alerts)\n");
        } else {
            for (Alert a : top.stream().limit(MAX_ALERTS).toList()) {
                sb.append("  - [").append(nz(a.getSeverity())).append("] ").append(nz(a.getTitle()));
                if (a.getSourceAgent() != null) sb.append(" (via ").append(a.getSourceAgent()).append(")");
                if (a.getDaysOverdue() != null && a.getDaysOverdue() > 0) sb.append(" — ").append(a.getDaysOverdue()).append("d overdue");
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String crsSection(Long portfolioId) {
        List<Object[]> rows = issues.findOpenAmCrRows(portfolioId);
        Map<String, Integer> byStage = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String stage = r[1] == null ? "Unstaged" : r[1].toString();
            byStage.merge(stage, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("OPEN CRs: ").append(rows.size());
        if (!byStage.isEmpty()) {
            sb.append(" (");
            sb.append(byStage.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .reduce((a, b) -> a + " · " + b).orElse(""));
            sb.append(")");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String prodBugsSection(Long portfolioId) {
        Map<String, Integer> bySeverity = new LinkedHashMap<>();
        for (Object[] r : issues.countOpenProdBugsByPortfolioAndSeverity()) {
            Long pf = r[0] == null ? null : ((Number) r[0]).longValue();
            if (portfolioId != null && !portfolioId.equals(pf)) continue;
            String sev = r[1] == null ? "Unset" : r[1].toString();
            bySeverity.merge(sev, ((Number) r[2]).intValue(), Integer::sum);
        }
        int total = bySeverity.values().stream().mapToInt(Integer::intValue).sum();
        StringBuilder sb = new StringBuilder();
        sb.append("OPEN PROD BUGS: ").append(total);
        if (!bySeverity.isEmpty()) {
            sb.append(" (");
            sb.append(bySeverity.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .reduce((a, b) -> a + " · " + b).orElse(""));
            sb.append(")");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String capacitySection() {
        List<Developer> devs = developers.findAllByOrderByUtilizationDesc();
        long overloaded = devs.stream().filter(d -> util(d) >= OVERLOAD_UTIL).count();
        long onLeave = devs.stream().filter(d -> Boolean.TRUE.equals(d.getOnLeave())).count();
        int maxUtil = devs.stream().mapToInt(this::util).max().orElse(0);
        return "CAPACITY: " + devs.size() + " devs, " + overloaded + " over " + OVERLOAD_UTIL
            + "% util (peak " + maxUtil + "%), " + onLeave + " on leave.\n";
    }

    private int util(Developer d) {
        return d.getUtilization() == null ? 0 : d.getUtilization();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
