package com.orbit.service.agent;

import com.orbit.domain.client.Project;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.service.agent.tool.AgentRunContext;
import com.orbit.service.agent.tool.SlackSendChannelTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DevReminderAgent {

    private static final Logger log = LoggerFactory.getLogger(DevReminderAgent.class);

    private final JiraIssueRepository issues;
    private final ProjectRepository projects;
    private final SlackSendChannelTool slackTool;

    public DevReminderAgent(JiraIssueRepository issues,
                            ProjectRepository projects,
                            SlackSendChannelTool slackTool) {
        this.issues = issues;
        this.projects = projects;
        this.slackTool = slackTool;
    }

    @Scheduled(cron = "${orbit.agents.dev-reminder.cron:0 0 9 * * MON-FRI}")
    public void run() {
        List<Project> activeProjects = projects.findByActiveTrue();
        for (Project project : activeProjects) {
            try {
                remind(project);
            } catch (Exception e) {
                log.warn("DevReminderAgent: error for project {} — {}", project.getName(), e.getMessage());
            }
        }
    }

    private void remind(Project project) {
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(5);
        List<JiraIssue> overdue = issues.findOverdueByProjectId(project.getId(), cutoff);
        if (overdue.isEmpty()) return;

        Map<String, List<JiraIssue>> byAssignee = overdue.stream()
            .collect(Collectors.groupingBy(i -> i.getAssigneeName() != null ? i.getAssigneeName() : "Unassigned"));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("*[Orbit] Overdue items for %s — %d item%s need attention*\n",
            project.getName(), overdue.size(), overdue.size() == 1 ? "" : "s"));

        byAssignee.forEach((assignee, items) -> {
            sb.append(String.format("\n*%s* (%d):\n", assignee, items.size()));
            items.stream().limit(5).forEach(i ->
                sb.append(String.format("  • %s — %s (%s)\n",
                    i.getIssueKey(),
                    i.getSummary() != null ? truncate(i.getSummary(), 60) : "(no summary)",
                    i.getLifecycleStage() != null ? i.getLifecycleStage() : "unknown stage"))
            );
            if (items.size() > 5) sb.append(String.format("  _...and %d more_\n", items.size() - 5));
        });

        Map<String, Object> args = new HashMap<>();
        args.put("message", sb.toString());
        AgentRunContext ctx = new AgentRunContext(null, null, project.getId(), "DevReminderAgent");
        Map<String, Object> result = slackTool.execute(args, ctx);
        boolean sent = Boolean.TRUE.equals(result.get("ok"));
        log.info("DevReminderAgent: reminder for project {} sent={} ({} overdue items)",
            project.getName(), sent, overdue.size());
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
