package com.orbit.controller;

import com.orbit.domain.report.ReportSchedule;
import com.orbit.repository.ClientRepository;
import com.orbit.repository.ReportScheduleRepository;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/report-schedules")
public class ReportScheduleController {

    private final ReportScheduleRepository schedules;
    private final ClientRepository clients;

    public ReportScheduleController(ReportScheduleRepository schedules, ClientRepository clients) {
        this.schedules = schedules; this.clients = clients;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<Map<String,Object>> list(
            @RequestParam(required=false) Long clientId,
            @RequestParam(required=false) Boolean active,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        Page<Map<String,Object>> all = schedules.findFiltered(clientId, PageRequest.of(page, size)).map(this::toResponse);
        if (active == null) return all;
        // In-memory filter on active flag — `report_schedules` table is small (<100 rows).
        var filtered = all.getContent().stream()
            .filter(m -> Boolean.valueOf(active.toString()).equals(m.get("active")))
            .toList();
        return new org.springframework.data.domain.PageImpl<>(filtered, PageRequest.of(page, size), filtered.size());
    }

    @GetMapping("/count")
    @PreAuthorize("isAuthenticated()")
    public Map<String,Object> count(@RequestParam(required=false) Boolean active) {
        long total = active == null
            ? schedules.count()
            : schedules.countByActive(active);
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("count", total);
        return m;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> create(@RequestBody Map<String,Object> body) {
        ReportSchedule s = new ReportSchedule();
        s.setReportType((String) body.getOrDefault("reportType", "Weekly delivery"));
        s.setCronExpression((String) body.getOrDefault("cronExpression", "0 0 8 * * MON"));
        s.setIncludeClientSafeFilter(Boolean.TRUE.equals(body.getOrDefault("clientSafeFilter", true)));
        s.setActive(true);
        if (body.get("clientId") != null)
            clients.findById(Long.valueOf(body.get("clientId").toString())).ifPresent(s::setClient);
        if (body.get("recipients") instanceof List<?> list) {
            s.setRecipients(list.stream().map(Object::toString).toArray(String[]::new));
        }
        schedules.save(s);
        return ResponseEntity.ok(toResponse(s));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        return schedules.findById(id).map(s -> {
            if (body.get("cronExpression") != null) s.setCronExpression((String) body.get("cronExpression"));
            if (body.get("active") != null) s.setActive(Boolean.parseBoolean(body.get("active").toString()));
            if (body.get("reportType") != null) s.setReportType((String) body.get("reportType"));
            if (body.get("recipients") instanceof List<?> list)
                s.setRecipients(list.stream().map(Object::toString).toArray(String[]::new));
            schedules.save(s);
            return ResponseEntity.ok(toResponse(s));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        schedules.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Map<String,Object> toResponse(ReportSchedule s) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("reportType", s.getReportType());
        m.put("client", s.getClient() != null ? s.getClient().getName() : "All clients");
        m.put("cronExpression", s.getCronExpression());
        m.put("recipients", s.getRecipients() != null ? Arrays.asList(s.getRecipients()) : List.of());
        m.put("active", s.getActive());
        m.put("lastRunAt", s.getLastRunAt() != null ? s.getLastRunAt().toString() : null);
        m.put("nextRunAt", s.getNextRunAt() != null ? s.getNextRunAt().toString() : null);
        return m;
    }
}
