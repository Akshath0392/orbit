package com.orbit.controller;

import com.orbit.domain.config.ReportTemplate;
import com.orbit.repository.ReportTemplateRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Export templates. A template is an ordered list of
 * {key, enabled} sections; renderers (account Delivery Report page) honor it
 * at request time — no redeploy to change an export's shape.
 */
@RestController
@RequestMapping("/api/v1/report-templates")
public class ReportTemplateController {

    private final ReportTemplateRepository templates;

    public ReportTemplateController(ReportTemplateRepository templates) {
        this.templates = templates;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PM','ADMIN','CSM','LEADERSHIP')")
    public List<ReportTemplate> list(@RequestParam(defaultValue = "acct") String scope) {
        return templates.findByScopeOrderByName(scope);
    }

    @GetMapping("/default")
    @PreAuthorize("hasAnyRole('PM','ADMIN','CSM','LEADERSHIP')")
    public ResponseEntity<ReportTemplate> defaultFor(@RequestParam(defaultValue = "acct") String scope) {
        return templates.findFirstByScopeAndDefaultTemplateTrue(scope)
            .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportTemplate> update(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body,
                                                  HttpServletRequest req) {
        return templates.findById(id).map(t -> {
            if (body.containsKey("name")) t.setName((String) body.get("name"));
            if (body.containsKey("sections")) t.setSections((String) body.get("sections"));
            t.setUpdatedBy(req.getUserPrincipal() != null ? req.getUserPrincipal().getName() : "unknown");
            t.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(templates.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }
}
