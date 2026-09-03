package com.orbit.service.agent.tool;

import com.orbit.repository.JiraIssueRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class OrbitGetOverdueItemsTool implements AgentTool {

    private final JiraIssueRepository issues;

    public OrbitGetOverdueItemsTool(JiraIssueRepository issues) {
        this.issues = issues;
    }

    @Override
    public String id() { return "orbit.get_overdue_items"; }

    @Override
    public String description() { return "Get CRs and bugs past SLA or hold threshold"; }

    @Override
    public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        long holdCrs = issues.findCrs(null, "Hold", null, Pageable.ofSize(100)).getTotalElements();
        return Map.of(
            "holdCrs", holdCrs,
            "checkedAt", LocalDateTime.now().toString()
        );
    }
}
