package com.orbit.controller;

import com.orbit.domain.alert.Alert;
import com.orbit.domain.alert.AlertNote;
import com.orbit.repository.AlertNoteRepository;
import com.orbit.repository.AlertRepository;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertRepository alerts;
    private final AlertNoteRepository alertNotes;
    public AlertController(AlertRepository alerts, AlertNoteRepository alertNotes) {
        this.alerts = alerts; this.alertNotes = alertNotes;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Page<Map<String,Object>> list(
            @RequestParam(required=false) String severity,
            @RequestParam(required=false) String status,
            @RequestParam(required=false) String type,
            @RequestParam(required=false) Long clientId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return alerts.findFiltered(severity, status, clientId, type, PageRequest.of(page, size))
            .map(this::toResponse);
    }

    @GetMapping("/types")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public List<String> types() {
        return alerts.findDistinctAlertTypes();
    }

    @PostMapping("/{id}/note")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> addNote(@PathVariable Long id,
                                      @RequestBody Map<String,Object> body,
                                      Authentication auth) {
        String text = (String) body.get("note");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error","note is required"));
        }
        return alerts.findById(id).map(a -> {
            AlertNote n = new AlertNote();
            n.setAlertId(id);
            n.setNote(text);
            n.setCreatedBy(auth.getName());
            alertNotes.save(n);
            a.setMitigationNote(text);
            alerts.save(a);
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", n.getId()); m.put("alertId", id);
            m.put("note", n.getNote()); m.put("by", n.getCreatedBy());
            m.put("at", n.getCreatedAt().toString());
            return ResponseEntity.ok((Object) m);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public List<Map<String,Object>> notes(@PathVariable Long id) {
        return alertNotes.findByAlertIdOrderByCreatedAtDesc(id).stream().map(n -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", n.getId()); m.put("note", n.getNote());
            m.put("by", n.getCreatedBy()); m.put("at", n.getCreatedAt().toString());
            return m;
        }).toList();
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> ack(@PathVariable Long id, Authentication auth) {
        return alerts.findById(id).map(a -> {
            a.setStatus("ACKNOWLEDGED");
            a.setOwnerName(auth.getName());
            alerts.save(a);
            return ResponseEntity.ok(toResponse(a));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> assign(@PathVariable Long id,
                                     @RequestBody Map<String,Object> body,
                                     Authentication auth) {
        if (body.get("followUpDate") == null) {
            return ResponseEntity.badRequest().body(Map.of("error","followUpDate is required"));
        }
        return alerts.findById(id).map(a -> {
            a.setStatus("ACKNOWLEDGED");
            a.setOwnerName((String) body.getOrDefault("ownerId", auth.getName()));
            a.setFollowUpDate(LocalDate.parse((String) body.get("followUpDate")));
            alerts.save(a);
            return ResponseEntity.ok(toResponse(a));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> dismiss(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        return alerts.findById(id).map(a -> {
            a.setStatus("DISMISSED");
            a.setMitigationNote((String) body.getOrDefault("reason", "Dismissed"));
            alerts.save(a);
            return ResponseEntity.ok(toResponse(a));
        }).orElse(ResponseEntity.notFound().build());
    }

    private Map<String,Object> toResponse(Alert a) {
        long minAgo = a.getCreatedAt() != null
            ? java.time.Duration.between(a.getCreatedAt(), java.time.LocalDateTime.now()).toMinutes() : 0;
        String time = minAgo < 60 ? minAgo + "m ago" : (minAgo/60) + "h ago";
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("type", a.getAlertType());
        m.put("sev", a.getSeverity());
        m.put("title", a.getTitle());
        m.put("detail", a.getDetail());
        m.put("client", a.getClient() != null ? a.getClient().getName() : "All");
        m.put("status", a.getStatus());
        m.put("time", time);
        m.put("agent", a.getSourceAgent());
        m.put("owner", a.getOwnerName());
        m.put("followUpDate", a.getFollowUpDate());
        m.put("phase", a.getPhase());
        m.put("daysOverdue", a.getDaysOverdue());
        return m;
    }
}
