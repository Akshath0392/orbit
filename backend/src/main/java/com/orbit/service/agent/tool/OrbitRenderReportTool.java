package com.orbit.service.agent.tool;

import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.ManDayBudgetRepository;
import com.orbit.repository.ManDaySnapshotRepository;
import com.orbit.repository.ProjectRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class OrbitRenderReportTool implements AgentTool {

    private final ProjectRepository projects;
    private final JiraIssueRepository issues;
    private final ManDayBudgetRepository budgets;
    private final ManDaySnapshotRepository snapshots;

    public OrbitRenderReportTool(ProjectRepository projects, JiraIssueRepository issues,
                                  ManDayBudgetRepository budgets, ManDaySnapshotRepository snapshots) {
        this.projects = projects;
        this.issues = issues;
        this.budgets = budgets;
        this.snapshots = snapshots;
    }

    @Override public String id()            { return "orbit.render_report_docx"; }
    @Override public String description()   { return "Generate an inline delivery status report for a project"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        Long projectId = ctx != null ? ctx.getProjectId() : null;
        if (args.containsKey("projectId")) {
            try { projectId = Long.parseLong(String.valueOf(args.get("projectId"))); } catch (Exception ignored) {}
        }
        if (projectId == null) return Map.of("error", "projectId_required");

        final Long pid = projectId;
        String projectName = projects.findById(pid).map(p -> p.getName()).orElse("Project #" + pid);
        Long clientId = projects.findById(pid).map(p -> p.getClient() != null ? p.getClient().getId() : null).orElse(null);

        long totalCrs  = issues.countByClientIdAndIssueType(clientId != null ? clientId : 0L, "CR");
        long openBugs  = issues.countByClientIdAndIssueType(clientId != null ? clientId : 0L, "PROD_BUG");
        long p0Bugs    = issues.countByClientIdAndIssueTypeAndSeverityIn(clientId != null ? clientId : 0L, "PROD_BUG", List.of("P0"));
        long breached  = issues.countProdBugsBySlaStatus(clientId, "Breached");

        String mdStatus = budgets.findByProjectId(pid).map(b -> {
            var snaps = snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(pid);
            if (snaps.isEmpty()) return "No burn data";
            var latest = snaps.get(0);
            BigDecimal purchased = b.getPurchasedDays() != null ? b.getPurchasedDays() : BigDecimal.ONE;
            BigDecimal burned = latest.getBurnedDays() != null ? latest.getBurnedDays() : BigDecimal.ZERO;
            int pct = burned.multiply(BigDecimal.valueOf(100)).divide(purchased, 0, RoundingMode.HALF_UP).intValue();
            return pct + "% of " + purchased.stripTrailingZeros().toPlainString() + " days consumed";
        }).orElse("Budget not configured");

        String report = String.format(
            "# Delivery Status Report — %s\nDate: %s\n\n" +
            "## CR Summary\nTotal CRs: %d\n\n" +
            "## Bug Summary\nOpen prod bugs: %d (P0: %d) | SLA breached: %d\n\n" +
            "## Man-Day Status\n%s\n",
            projectName, LocalDate.now(), totalCrs, openBugs, p0Bugs, breached, mdStatus
        );

        return Map.of(
            "projectId", pid,
            "projectName", projectName,
            "report", report,
            "format", "markdown",
            "note", "inline markdown — Word/PDF export requires Apache POI integration"
        );
    }
}
