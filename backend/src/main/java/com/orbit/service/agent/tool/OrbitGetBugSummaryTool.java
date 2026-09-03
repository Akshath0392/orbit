package com.orbit.service.agent.tool;

import com.orbit.repository.JiraIssueRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OrbitGetBugSummaryTool implements AgentTool {

    private final JiraIssueRepository issues;

    public OrbitGetBugSummaryTool(JiraIssueRepository issues) { this.issues = issues; }

    @Override public String id()            { return "orbit.get_bug_summary"; }
    @Override public String description()   { return "Bug count, SLA status breakdown, and P0/P1 summary for a project or client"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        Long clientId = null;
        if (args.containsKey("clientId")) {
            try { clientId = Long.parseLong(String.valueOf(args.get("clientId"))); } catch (Exception ignored) {}
        }

        long p0 = issues.countByClientIdAndIssueTypeAndSeverityIn(
            clientId != null ? clientId : 0L, "PROD_BUG", List.of("P0"));
        long p1 = issues.countByClientIdAndIssueTypeAndSeverityIn(
            clientId != null ? clientId : 0L, "PROD_BUG", List.of("P1"));
        long breached = issues.countProdBugsBySlaStatus(clientId, "Breached");
        long atRisk   = issues.countProdBugsBySlaStatus(clientId, "At risk");
        long total    = issues.countByClientIdAndIssueType(clientId != null ? clientId : 0L, "PROD_BUG");
        long uatBugs  = issues.countByClientIdAndIssueType(clientId != null ? clientId : 0L, "UAT_BUG");

        return Map.of(
            "totalProdBugs", total,
            "p0Open", p0,
            "p1Open", p1,
            "slaBreached", breached,
            "slaAtRisk", atRisk,
            "uatBugsOpen", uatBugs
        );
    }
}
