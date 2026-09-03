package com.orbit.service.agent.tool;

import com.orbit.repository.ManDaySnapshotRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrbitGetManDayHistoryTool implements AgentTool {

    private final ManDaySnapshotRepository snapshots;

    public OrbitGetManDayHistoryTool(ManDaySnapshotRepository snapshots) { this.snapshots = snapshots; }

    @Override public String id()            { return "orbit.get_man_day_history"; }
    @Override public String description()   { return "Historical daily man-day burn snapshots for a project"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        Long projectId = ctx != null ? ctx.getProjectId() : null;
        if (args.containsKey("projectId")) {
            try { projectId = Long.parseLong(String.valueOf(args.get("projectId"))); } catch (Exception ignored) {}
        }
        if (projectId == null) return Map.of("error", "projectId_required");

        final Long pid = projectId;
        List<Map<String, Object>> history = snapshots
            .findTop14ByProjectIdOrderBySnapshotDateDesc(pid)
            .stream()
            .map(s -> Map.<String, Object>of(
                "date", s.getSnapshotDate() != null ? s.getSnapshotDate().toString() : "",
                "burnedDays", s.getBurnedDays() != null ? s.getBurnedDays() : 0,
                "remainingDays", s.getRemainingDays() != null ? s.getRemainingDays() : 0,
                "burnRatePerDay", s.getBurnRatePerDay() != null ? s.getBurnRatePerDay() : 0
            )).collect(Collectors.toList());

        return Map.of("projectId", pid, "snapshots", history, "count", history.size());
    }
}
