package com.orbit.service.agent.tool;

import com.orbit.repository.LeaveRecordRepository;
import com.orbit.repository.WfhRecordRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrbitGetLeaveTodayTool implements AgentTool {

    private final LeaveRecordRepository leaves;
    private final WfhRecordRepository   wfhRecords;

    public OrbitGetLeaveTodayTool(LeaveRecordRepository leaves, WfhRecordRepository wfhRecords) {
        this.leaves = leaves; this.wfhRecords = wfhRecords;
    }

    @Override public String id()            { return "orbit.get_leave_today"; }
    @Override public String description()   { return "Team members on leave today"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        LocalDate today = LocalDate.now();
        List<String> onLeave = leaves.findByStartDateBetweenOrderByStartDateAsc(today, today)
            .stream()
            .map(l -> l.getUser() != null ? l.getUser().getName() : l.getDarwinEmpId())
            .collect(Collectors.toList());

        List<Map<String, Object>> wfhToday = wfhRecords
            .findByWfhDateBetweenOrderByWfhDateAsc(today, today)
            .stream()
            .filter(w -> !"REJECTED".equalsIgnoreCase(w.getStatus()) && !"CANCELLED".equalsIgnoreCase(w.getStatus()))
            .map(w -> Map.<String, Object>of(
                "name", w.getUser() != null ? w.getUser().getName() : w.getDarwinEmpId(),
                "wfhType", w.getWfhType() != null ? w.getWfhType() : "FULL_DAY",
                "status", w.getStatus()
            )).collect(java.util.stream.Collectors.toList());

        return Map.of(
            "date", today.toString(),
            "onLeave", onLeave,
            "leaveCount", onLeave.size(),
            "wfhToday", wfhToday,
            "wfhCount", wfhToday.size()
        );
    }
}
