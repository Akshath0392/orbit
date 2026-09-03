package com.orbit.service.agent.tool;

import com.orbit.repository.JiraIssueRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrbitGetCrSummaryTool implements AgentTool {

    private final JiraIssueRepository issues;

    public OrbitGetCrSummaryTool(JiraIssueRepository issues) {
        this.issues = issues;
    }

    @Override
    public String id() { return "orbit.get_cr_summary"; }

    @Override
    public String description() { return "Get CR summary for a project or client"; }

    @Override
    public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        Long projectId = ctx.getProjectId();
        long total = issues.findCrs(null, null, null, Pageable.ofSize(1)).getTotalElements();
        long hold  = issues.findCrs(null, "Hold", null, Pageable.ofSize(1)).getTotalElements();
        return Map.of(
            "totalCrs", total,
            "onHold", hold,
            "projectId", projectId != null ? projectId : "all"
        );
    }
}
