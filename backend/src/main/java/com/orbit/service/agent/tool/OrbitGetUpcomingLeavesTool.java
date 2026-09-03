package com.orbit.service.agent.tool;

import com.orbit.repository.LeaveRecordRepository;
import com.orbit.repository.WfhRecordRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrbitGetUpcomingLeavesTool implements AgentTool {

    private final LeaveRecordRepository leaves;
    private final WfhRecordRepository   wfhRecords;

    public OrbitGetUpcomingLeavesTool(LeaveRecordRepository leaves, WfhRecordRepository wfhRecords) {
        this.leaves = leaves; this.wfhRecords = wfhRecords;
    }

    @Override public String id()            { return "orbit.get_upcoming_leaves"; }
    @Override public String description()   { return "Approved leave in the next 30 days"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(30);

        List<Map<String, Object>> upcoming = leaves
            .findByStartDateBetweenOrderByStartDateAsc(today, horizon)
            .stream()
            .filter(l -> !"CANCELLED".equalsIgnoreCase(l.getStatus()) && !"REJECTED".equalsIgnoreCase(l.getStatus()))
            .map(l -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("person", l.getUser() != null ? l.getUser().getName() : l.getDarwinEmpId());
                m.put("leaveType", l.getLeaveType());
                m.put("from", l.getStartDate() != null ? l.getStartDate().toString() : null);
                m.put("to", l.getEndDate() != null ? l.getEndDate().toString() : null);
                m.put("workingDays", l.getWorkingDays());
                m.put("status", l.getStatus());
                return m;
            }).collect(Collectors.toList());

        List<Map<String, Object>> upcomingWfh = wfhRecords
            .findByWfhDateBetweenOrderByWfhDateAsc(today, horizon)
            .stream()
            .filter(w -> !"REJECTED".equalsIgnoreCase(w.getStatus()) && !"CANCELLED".equalsIgnoreCase(w.getStatus()))
            .map(w -> {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("person", w.getUser() != null ? w.getUser().getName() : w.getDarwinEmpId());
                m.put("wfhDate", w.getWfhDate() != null ? w.getWfhDate().toString() : null);
                m.put("wfhType", w.getWfhType());
                m.put("status", w.getStatus());
                m.put("reason", w.getReason());
                return m;
            }).collect(java.util.stream.Collectors.toList());

        return Map.of(
            "horizonDays", 30,
            "fromDate", today.toString(),
            "toDate", horizon.toString(),
            "leaves", upcoming,
            "leaveCount", upcoming.size(),
            "wfh", upcomingWfh,
            "wfhCount", upcomingWfh.size()
        );
    }
}
