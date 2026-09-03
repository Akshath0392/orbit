package com.orbit.service.agent.tool;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;

@Component
public class OrbitGetSprintRemainingDaysTool implements AgentTool {

    @Override public String id()            { return "orbit.get_sprint_remaining_days"; }
    @Override public String description()   { return "Business days remaining in the current 2-week sprint"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        LocalDate today = LocalDate.now();
        LocalDate sprintStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int week = today.get(java.time.temporal.WeekFields.ISO.weekOfYear());
        if (week % 2 == 0) sprintStart = sprintStart.minusWeeks(1);
        LocalDate sprintEnd = sprintStart.plusWeeks(2).minusDays(1);

        long remaining = sprintStart.datesUntil(sprintEnd.plusDays(1))
            .filter(d -> d.isAfter(today.minusDays(1))
                && d.getDayOfWeek() != DayOfWeek.SATURDAY
                && d.getDayOfWeek() != DayOfWeek.SUNDAY)
            .count();

        return Map.of(
            "sprintStart", sprintStart.toString(),
            "sprintEnd", sprintEnd.toString(),
            "businessDaysRemaining", remaining,
            "today", today.toString()
        );
    }
}
