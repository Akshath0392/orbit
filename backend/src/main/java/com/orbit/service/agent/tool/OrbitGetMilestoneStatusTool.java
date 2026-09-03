package com.orbit.service.agent.tool;

import com.orbit.repository.IssueMilestoneRepository;
import com.orbit.repository.JiraIssueRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrbitGetMilestoneStatusTool implements AgentTool {

    private final JiraIssueRepository issues;
    private final IssueMilestoneRepository milestones;

    public OrbitGetMilestoneStatusTool(JiraIssueRepository issues, IssueMilestoneRepository milestones) {
        this.issues = issues;
        this.milestones = milestones;
    }

    @Override public String id()            { return "orbit.get_milestone_status"; }
    @Override public String description()   { return "Milestone dates and status for a project or specific CR"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        Long projectId = ctx != null ? ctx.getProjectId() : null;
        if (args.containsKey("projectId")) {
            try { projectId = Long.parseLong(String.valueOf(args.get("projectId"))); } catch (Exception ignored) {}
        }

        String issueKey = String.valueOf(args.getOrDefault("issueKey", ""));
        if (!issueKey.isBlank()) {
            return issues.findByIssueKey(issueKey).map(issue -> {
                List<Map<String, Object>> ms = milestones.findByIssueId(issue.getId()).stream()
                    .map(this::milestoneMap).collect(Collectors.toList());
                return (Map<String, Object>) Map.of("issueKey", issueKey, "milestones", ms);
            }).orElse(Map.of("error", "issue_not_found"));
        }

        if (projectId == null) return Map.of("error", "projectId_or_issueKey_required");

        LocalDate today = LocalDate.now();
        long tbcCount    = milestones.countByIssueProjectIdAndIsTbcTrue(projectId);
        long overdueCount = milestones.countOverdueByProjectId(projectId);

        return Map.of(
            "projectId", projectId,
            "tbcMilestones", tbcCount,
            "overdueMilestones", overdueCount,
            "asOf", today.toString()
        );
    }

    private Map<String, Object> milestoneMap(com.orbit.domain.issue.IssueMilestone ml) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", ml.getMilestoneType());
        m.put("targetDate", ml.getTargetDate() != null ? ml.getTargetDate().toString() : null);
        m.put("isTbc", Boolean.TRUE.equals(ml.getIsTbc()));
        m.put("status", ml.getStatus());
        return m;
    }
}
