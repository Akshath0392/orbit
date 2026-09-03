package com.orbit.service.agent.tool;

import com.orbit.repository.IssueMilestoneRepository;
import com.orbit.repository.JiraIssueRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OrbitGetReleaseCalendarTool implements AgentTool {

    private final JiraIssueRepository issues;
    private final IssueMilestoneRepository milestones;

    public OrbitGetReleaseCalendarTool(JiraIssueRepository issues, IssueMilestoneRepository milestones) {
        this.issues = issues;
        this.milestones = milestones;
    }

    @Override public String id()            { return "orbit.get_release_calendar"; }
    @Override public String description()   { return "Upcoming milestone and release dates across projects"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(60);

        List<Map<String, Object>> upcoming = new ArrayList<>();

        issues.findByIssueTypeOrderByUpdatedAtDesc("CR",
            org.springframework.data.domain.PageRequest.of(0, 50))
            .forEach(issue -> {
                milestones.findByIssueId(issue.getId()).stream()
                    .filter(ml -> ml.getTargetDate() != null
                        && !ml.getTargetDate().isBefore(today)
                        && !ml.getTargetDate().isAfter(horizon)
                        && !"COMPLETED".equals(ml.getStatus()))
                    .forEach(ml -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("issueKey", issue.getIssueKey());
                        m.put("summary", issue.getSummary());
                        m.put("client", issue.getClient() != null ? issue.getClient().getName() : null);
                        m.put("milestoneType", ml.getMilestoneType());
                        m.put("targetDate", ml.getTargetDate().toString());
                        m.put("isTbc", Boolean.TRUE.equals(ml.getIsTbc()));
                        m.put("status", ml.getStatus());
                        upcoming.add(m);
                    });
            });

        upcoming.sort((a, b) -> String.valueOf(a.get("targetDate")).compareTo(String.valueOf(b.get("targetDate"))));

        return Map.of(
            "upcomingMilestones", upcoming,
            "count", upcoming.size(),
            "horizonDays", 60,
            "asOf", today.toString()
        );
    }
}
