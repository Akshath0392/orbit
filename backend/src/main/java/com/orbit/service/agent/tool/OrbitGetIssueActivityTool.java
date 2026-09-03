package com.orbit.service.agent.tool;

import com.orbit.repository.JiraIssueRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrbitGetIssueActivityTool implements AgentTool {

    private final JiraIssueRepository issues;

    public OrbitGetIssueActivityTool(JiraIssueRepository issues) { this.issues = issues; }

    @Override public String id()            { return "orbit.get_issue_activity_since_yesterday"; }
    @Override public String description()   { return "Issues updated since yesterday — Jira transitions and status changes"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        Long projectId = ctx != null ? ctx.getProjectId() : null;
        LocalDateTime since = LocalDate.now().minusDays(1).atStartOfDay();

        List<Map<String, Object>> activity = issues.findByProjectIdAndUpdatedAtAfter(
                projectId, since, PageRequest.of(0, 30))
            .stream()
            .map(i -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", i.getIssueKey());
                m.put("summary", i.getSummary());
                m.put("status", i.getJiraStatus());
                m.put("lifecycleStage", i.getLifecycleStage());
                m.put("assignee", i.getAssigneeName());
                m.put("updatedAt", i.getUpdatedAt() != null ? i.getUpdatedAt().toString() : null);
                m.put("issueType", i.getIssueType());
                return m;
            }).collect(Collectors.toList());

        return Map.of(
            "since", since.toString(),
            "count", activity.size(),
            "items", activity,
            "projectId", projectId != null ? projectId : "all"
        );
    }
}
