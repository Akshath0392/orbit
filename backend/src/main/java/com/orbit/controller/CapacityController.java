package com.orbit.controller;

import com.orbit.repository.DeveloperRepository;
import com.orbit.repository.LeaveRecordRepository;
import com.orbit.service.hrms.HrmsSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/capacity")
public class CapacityController {

    private final DeveloperRepository  developers;
    private final LeaveRecordRepository leaveRecords;
    private final HrmsSyncService       hrms;

    @Value("${akki.capacity.overload-threshold:85}") private int overloadThreshold;
    @Value("${akki.capacity.busy-threshold:70}")     private int busyThreshold;
    @Value("${akki.capacity.man-day-warning-pct:80}") private int manDayWarningPct;
    @Value("${akki.uat.cycle-warn-threshold:3}")     private int uatCycleWarnThreshold;

    public CapacityController(DeveloperRepository developers, LeaveRecordRepository leaveRecords,
                              HrmsSyncService hrms) {
        this.developers = developers; this.leaveRecords = leaveRecords; this.hrms = hrms;
    }

    @GetMapping("/config")
    @PreAuthorize("isAuthenticated()")
    public Map<String,Object> config() {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("overloadThreshold",   overloadThreshold);
        m.put("busyThreshold",       busyThreshold);
        m.put("manDayWarningPct",    manDayWarningPct);
        m.put("uatCycleWarnThreshold", uatCycleWarnThreshold);
        return m;
    }

    @GetMapping("/teams")
    @PreAuthorize("hasAnyRole('PM','ENGINEERING','ADMIN')")
    public List<String> teams() { return developers.findDistinctTeams(); }

    @GetMapping("/portfolio-summary")
    @PreAuthorize("hasAnyRole('PM','ENGINEERING','REVENUE','CSM','LEADERSHIP','ADMIN')")
    public Map<String,Object> portfolioSummary(@RequestParam(required = false) Long portfolioId) {
        // Portfolio-scoped capacity isn't joined to projects yet — return global aggregate
        // tagged with the requested portfolioId so the consumer sees a deterministic shape.
        var devList = developers.findAllByOrderByUtilizationDesc();
        long overloaded = devList.stream().filter(d -> d.getUtilization() != null && d.getUtilization() >  overloadThreshold).count();
        long busy       = devList.stream().filter(d -> d.getUtilization() != null && d.getUtilization() >  busyThreshold && d.getUtilization() <= overloadThreshold).count();
        long onLeave    = devList.stream().filter(d -> Boolean.TRUE.equals(d.getOnLeave())).count();
        long available  = Math.max(0, devList.size() - overloaded - busy - onLeave);
        double avgUtil  = devList.stream()
            .filter(d -> d.getUtilization() != null && !Boolean.TRUE.equals(d.getOnLeave()))
            .mapToInt(d -> d.getUtilization()).average().orElse(0);
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("portfolioId", portfolioId);
        m.put("totalDevs",   devList.size());
        m.put("available",   available);
        m.put("busy",        busy);
        m.put("overloaded",  overloaded);
        m.put("onLeaveToday",onLeave);
        m.put("avgUtil",     (int) Math.round(avgUtil));
        return m;
    }

    @GetMapping("/team")
    @PreAuthorize("hasAnyRole('PM','ENGINEERING','ADMIN')")
    public List<Map<String, Object>> team(@RequestParam(required=false) String team) {
        var list = (team == null || team.isBlank() || "all".equalsIgnoreCase(team))
            ? developers.findAllByOrderByUtilizationDesc()
            : developers.findByTeamOrderByUtilizationDesc(team);
        return list.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",     d.getId());
            m.put("name",   d.getName());
            m.put("team",   d.getTeam() != null ? d.getTeam() : "");
            m.put("util",   d.getUtilization() != null ? d.getUtilization() : 0);
            m.put("tasks",  d.getActiveTasks() != null ? d.getActiveTasks() : 0);
            m.put("leave",  d.getLeavePeriod() != null ? d.getLeavePeriod() : "");
            m.put("onLeave",Boolean.TRUE.equals(d.getOnLeave()));
            m.put("av",     d.getInitials() != null ? d.getInitials() : "");
            m.put("color",  d.getAvatarColor() != null ? d.getAvatarColor() : "#6366F1");
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/leaves")
    @PreAuthorize("hasAnyRole('PM','ENGINEERING','ADMIN')")
    public List<Map<String, Object>> leaves() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");
        DateTimeFormatter fmtLong = DateTimeFormatter.ofPattern("MMM d, yyyy");
        String source = hrms.activeProviderName().orElse("HR sync");
        return leaveRecords.findUpcoming(LocalDate.now()).stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",        l.getId());
            m.put("dev",       l.getUser() != null ? l.getUser().getName() : "Unknown");
            m.put("av",        l.getUser() != null ? l.getUser().getInitials() : "?");
            m.put("color",     l.getUser() != null ? l.getUser().getAvatarColor() : "#6366F1");
            m.put("leaveType", l.getLeaveType());
            m.put("from",      l.getStartDate() != null ? l.getStartDate().format(fmt) : "");
            m.put("to",        l.getEndDate()   != null ? l.getEndDate().format(fmtLong) : "");
            m.put("days",      l.getWorkingDays());
            m.put("status",    l.getStatus());
            m.put("source",    source);
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/assignments")
    @PreAuthorize("hasAnyRole('PM','ENGINEERING','ADMIN')")
    public List<Map<String, Object>> assignments() { return List.of(); }
}
