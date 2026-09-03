package com.orbit.service.agent.tool;

import com.orbit.repository.DeveloperRepository;
import com.orbit.repository.LeaveRecordRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrbitGetTeamCapacityTool implements AgentTool {

    private final DeveloperRepository developers;
    private final LeaveRecordRepository leaves;

    public OrbitGetTeamCapacityTool(DeveloperRepository developers, LeaveRecordRepository leaves) {
        this.developers = developers;
        this.leaves = leaves;
    }

    @Override public String id()            { return "orbit.get_team_capacity"; }
    @Override public String description()   { return "Current team utilisation and leave status"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        LocalDate today = LocalDate.now();
        List<String> onLeaveToday = leaves.findByStartDateBetweenOrderByStartDateAsc(today, today)
            .stream()
            .map(l -> l.getUser() != null ? l.getUser().getName() : l.getDarwinEmpId())
            .collect(Collectors.toList());

        List<Map<String, Object>> devList = developers.findAllByOrderByUtilizationDesc().stream()
            .map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", d.getName());
                m.put("team", d.getTeam());
                m.put("utilization", d.getUtilization());
                m.put("activeTasks", d.getActiveTasks());
                m.put("onLeave", Boolean.TRUE.equals(d.getOnLeave()));
                m.put("leavePeriod", d.getLeavePeriod());
                return m;
            }).collect(Collectors.toList());

        long overloaded = devList.stream().filter(d -> (Integer) d.get("utilization") > 85).count();
        long available  = devList.stream().filter(d -> (Integer) d.get("utilization") < 70 && !(Boolean) d.get("onLeave")).count();

        return Map.of(
            "developers", devList,
            "totalCount", devList.size(),
            "overloaded", overloaded,
            "available", available,
            "onLeaveToday", onLeaveToday
        );
    }
}
