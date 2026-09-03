package com.orbit.controller;

import com.orbit.domain.alert.*;
import com.orbit.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/alert-rules")
@PreAuthorize("hasRole('ADMIN')")
public class AlertRulesController {

    private final NotificationRuleRepository ruleRepo;
    private final EscalationConfigRepository escalationRepo;
    private final GlobalSpocConfigRepository spocRepo;
    private final NotificationEventRepository eventRepo;

    public AlertRulesController(NotificationRuleRepository ruleRepo,
                                 EscalationConfigRepository escalationRepo,
                                 GlobalSpocConfigRepository spocRepo,
                                 NotificationEventRepository eventRepo) {
        this.ruleRepo = ruleRepo;
        this.escalationRepo = escalationRepo;
        this.spocRepo = spocRepo;
        this.eventRepo = eventRepo;
    }

    // ── Notification rules ──────────────────────────────────────────────────────

    @GetMapping("/rules")
    public List<Map<String, Object>> listRules() {
        return ruleRepo.findAll().stream().map(this::ruleMap).toList();
    }

    @PutMapping("/rules/{id}/toggle")
    public ResponseEntity<?> toggleRule(@PathVariable Long id) {
        return ruleRepo.findById(id).map(r -> {
            r.setEnabled(!Boolean.TRUE.equals(r.getEnabled()));
            ruleRepo.save(r);
            return ResponseEntity.ok(ruleMap(r));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<?> updateRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ruleRepo.findById(id).map(r -> {
            if (body.containsKey("triggerTime"))          r.setTriggerTime((String) body.get("triggerTime"));
            if (body.containsKey("overdueIntervalHours")) r.setOverdueIntervalHours((Integer) body.get("overdueIntervalHours"));
            if (body.containsKey("overdueWindowStart"))   r.setOverdueWindowStart((String) body.get("overdueWindowStart"));
            if (body.containsKey("overdueWindowEnd"))     r.setOverdueWindowEnd((String) body.get("overdueWindowEnd"));
            ruleRepo.save(r);
            return ResponseEntity.ok(ruleMap(r));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Escalation config ───────────────────────────────────────────────────────

    @GetMapping("/escalation")
    public List<Map<String, Object>> listEscalation() {
        return escalationRepo.findAll().stream().map(this::escalationMap).toList();
    }

    @PutMapping("/escalation/{id}")
    public ResponseEntity<?> updateEscalation(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return escalationRepo.findById(id).map(e -> {
            if (body.containsKey("phaseSpocEmail"))      e.setPhaseSpocEmail((String) body.get("phaseSpocEmail"));
            if (body.containsKey("phaseSpocName"))       e.setPhaseSpocName((String) body.get("phaseSpocName"));
            if (body.containsKey("deliverySpocEnabled")) e.setDeliverySpocEnabled((Boolean) body.get("deliverySpocEnabled"));
            if (body.containsKey("reEscalationHours"))   e.setReEscalationHours((Integer) body.get("reEscalationHours"));
            e.setUpdatedAt(LocalDateTime.now());
            escalationRepo.save(e);
            return ResponseEntity.ok(escalationMap(e));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Global SPOC config ──────────────────────────────────────────────────────

    @GetMapping("/spocs")
    public List<Map<String, Object>> listSpocs() {
        return spocRepo.findAll().stream().map(this::spocMap).toList();
    }

    @PutMapping("/spocs/{id}")
    public ResponseEntity<?> updateSpoc(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return spocRepo.findById(id).map(s -> {
            if (body.containsKey("email"))       s.setEmail((String) body.get("email"));
            if (body.containsKey("name"))        s.setName((String) body.get("name"));
            if (body.containsKey("slackUserId")) s.setSlackUserId((String) body.get("slackUserId"));
            s.setUpdatedAt(LocalDateTime.now());
            spocRepo.save(s);
            return ResponseEntity.ok(spocMap(s));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Notification event log ──────────────────────────────────────────────────

    @GetMapping("/events")
    public List<Map<String, Object>> recentEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return eventRepo.findAllByOrderBySentAtDesc(
            org.springframework.data.domain.PageRequest.of(page, size))
            .stream().map(this::eventMap).toList();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Map<String, Object> ruleMap(NotificationRule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("ruleName", r.getRuleName());
        m.put("triggerType", r.getTriggerType());
        m.put("role", r.getRole());
        m.put("phase", r.getPhase());
        m.put("offsetDays", r.getOffsetDays());
        m.put("triggerTime", r.getTriggerTime());
        m.put("enabled", r.getEnabled());
        m.put("overdueIntervalHours", r.getOverdueIntervalHours());
        m.put("overdueWindowStart", r.getOverdueWindowStart());
        m.put("overdueWindowEnd", r.getOverdueWindowEnd());
        return m;
    }

    private Map<String, Object> escalationMap(EscalationConfig e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("role", e.getRole());
        m.put("phase", e.getPhase());
        m.put("phaseSpocEmail", e.getPhaseSpocEmail());
        m.put("phaseSpocName", e.getPhaseSpocName());
        m.put("deliverySpocEnabled", e.getDeliverySpocEnabled());
        m.put("reEscalationHours", e.getReEscalationHours());
        return m;
    }

    private Map<String, Object> spocMap(GlobalSpocConfig s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("spocType", s.getSpocType());
        m.put("email", s.getEmail());
        m.put("name", s.getName());
        m.put("slackUserId", s.getSlackUserId());
        return m;
    }

    private Map<String, Object> eventMap(com.orbit.domain.alert.NotificationEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("phase", e.getPhase());
        m.put("eventType", e.getEventType());
        m.put("recipientEmail", e.getRecipientEmail());
        m.put("recipientName", e.getRecipientName());
        m.put("userResponse", e.getUserResponse());
        m.put("sentAt", e.getSentAt());
        m.put("respondedAt", e.getRespondedAt());
        m.put("project", e.getProject() != null ? e.getProject().getName() : null);
        return m;
    }
}
