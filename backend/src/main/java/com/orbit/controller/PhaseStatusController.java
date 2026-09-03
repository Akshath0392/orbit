package com.orbit.controller;

import com.orbit.domain.alert.PhaseStatus;
import com.orbit.domain.client.Project;
import com.orbit.repository.PhaseStatusRepository;
import com.orbit.repository.ProjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/phase-statuses")
public class PhaseStatusController {

    private final PhaseStatusRepository phaseRepo;
    private final ProjectRepository projectRepo;

    public PhaseStatusController(PhaseStatusRepository phaseRepo, ProjectRepository projectRepo) {
        this.phaseRepo = phaseRepo;
        this.projectRepo = projectRepo;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public List<Map<String, Object>> listAll() {
        return phaseRepo.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public List<Map<String, Object>> byProject(@PathVariable Long projectId) {
        return phaseRepo.findByProjectId(projectId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        Long projectId = toLong(body.get("projectId"));
        String phase = (String) body.get("phase");
        if (projectId == null || phase == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectId and phase are required"));
        }
        Optional<Project> proj = projectRepo.findById(projectId);
        if (proj.isEmpty()) return ResponseEntity.notFound().build();

        PhaseStatus ps = new PhaseStatus();
        ps.setProject(proj.get());
        ps.setPhase(phase.toUpperCase());
        applyFields(ps, body);
        phaseRepo.save(ps);
        return ResponseEntity.ok(toResponse(ps));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return phaseRepo.findById(id).map(ps -> {
            applyFields(ps, body);
            ps.setUpdatedAt(LocalDateTime.now());
            phaseRepo.save(ps);
            return ResponseEntity.ok(toResponse(ps));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return phaseRepo.findById(id).map(ps -> {
            String status = (String) body.get("status");
            String delayNote = (String) body.get("delayNote");
            if (status != null) ps.setStatus(status);
            if (delayNote != null) ps.setDelayNote(delayNote);
            ps.setUpdatedAt(LocalDateTime.now());
            phaseRepo.save(ps);
            return ResponseEntity.ok(toResponse(ps));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!phaseRepo.existsById(id)) return ResponseEntity.notFound().build();
        phaseRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void applyFields(PhaseStatus ps, Map<String, Object> body) {
        if (body.containsKey("startDate"))     ps.setStartDate(parseDate(body.get("startDate")));
        if (body.containsKey("endDate"))       ps.setEndDate(parseDate(body.get("endDate")));
        if (body.containsKey("assigneeEmail")) ps.setAssigneeEmail((String) body.get("assigneeEmail"));
        if (body.containsKey("assigneeName"))  ps.setAssigneeName((String) body.get("assigneeName"));
        if (body.containsKey("jiraIssueKey"))  ps.setJiraIssueKey((String) body.get("jiraIssueKey"));
        if (body.containsKey("status"))        ps.setStatus((String) body.get("status"));
        if (body.containsKey("delayNote"))     ps.setDelayNote((String) body.get("delayNote"));
    }

    private Map<String, Object> toResponse(PhaseStatus ps) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ps.getId());
        m.put("projectId",   ps.getProject() != null ? ps.getProject().getId() : null);
        m.put("projectName", ps.getProject() != null ? ps.getProject().getName() : null);
        m.put("phase",          ps.getPhase());
        m.put("startDate",      ps.getStartDate());
        m.put("endDate",        ps.getEndDate());
        m.put("assigneeEmail",  ps.getAssigneeEmail());
        m.put("assigneeName",   ps.getAssigneeName());
        m.put("status",         ps.getStatus());
        m.put("delayNote",      ps.getDelayNote());
        m.put("jiraIssueKey",   ps.getJiraIssueKey());
        m.put("ddayNotified",   ps.getDdayNotified());
        m.put("updatedAt",      ps.getUpdatedAt());
        return m;
    }

    private LocalDate parseDate(Object v) {
        return v != null ? LocalDate.parse(v.toString()) : null;
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }
}
