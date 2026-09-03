package com.orbit.service.agent.tool;

import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.IssueMilestoneRepository;
import com.orbit.repository.JiraIssueRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrbitGetIssueDetailTool implements AgentTool {

    private final JiraIssueRepository issues;
    private final IssueMilestoneRepository milestones;

    public OrbitGetIssueDetailTool(JiraIssueRepository issues, IssueMilestoneRepository milestones) {
        this.issues = issues;
        this.milestones = milestones;
    }

    @Override public String id()            { return "orbit.get_issue_detail"; }
    @Override public String description()   { return "Get full detail for a Jira issue by key"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        String issueKey = String.valueOf(args.getOrDefault("issueKey", ""));
        if (issueKey.isBlank()) return Map.of("error", "issueKey_required");

        return issues.findByIssueKey(issueKey).map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", i.getIssueKey());
            m.put("summary", i.getSummary());
            m.put("issueType", i.getIssueType());
            m.put("status", i.getJiraStatus());
            m.put("lifecycleStage", i.getLifecycleStage());
            m.put("severity", i.getSeverity());
            m.put("priority", i.getPriority());
            m.put("assignee", i.getAssigneeName());
            m.put("slaStatus", i.getSlaStatus());
            m.put("slaRemainingHours", i.getSlaRemainingHours());
            m.put("reopenCount", i.getReopenCount());
            m.put("holdReason", i.getHoldReason());
            m.put("client", i.getClient() != null ? i.getClient().getName() : null);
            m.put("createdAt", i.getCreatedAt() != null ? i.getCreatedAt().toString() : null);
            m.put("updatedAt", i.getUpdatedAt() != null ? i.getUpdatedAt().toString() : null);
            List<Map<String, Object>> ms = milestones.findByIssueId(i.getId()).stream().map(ml -> {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("type", ml.getMilestoneType());
                mm.put("targetDate", ml.getTargetDate() != null ? ml.getTargetDate().toString() : null);
                mm.put("isTbc", Boolean.TRUE.equals(ml.getIsTbc()));
                mm.put("status", ml.getStatus());
                return mm;
            }).collect(Collectors.toList());
            m.put("milestones", ms);
            return m;
        }).orElse(Map.of("error", "issue_not_found", "issueKey", issueKey));
    }
}
