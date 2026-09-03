package com.orbit.controller;

import com.orbit.repository.ProjectRepository;
import com.orbit.service.sync.JiraSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectRepository projects;
    private final JiraSyncService jiraSyncService;

    public ProjectController(ProjectRepository projects, JiraSyncService jiraSyncService) {
        this.projects = projects;
        this.jiraSyncService = jiraSyncService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> list() {
        return projects.findByActiveTrue().stream().map(p -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",               p.getId());
            m.put("name",             p.getName());
            m.put("clientId",         p.getClient() != null ? p.getClient().getId() : null);
            m.put("clientName",       p.getClient() != null ? p.getClient().getName() : "");
            m.put("portfolioName",    p.getPortfolio() != null ? p.getPortfolio().getName() : "");
            m.put("portfolioId",      p.getPortfolio() != null ? p.getPortfolio().getId() : null);
            m.put("jiraProjectKeys",  p.getJiraProjectKeys());
            m.put("jiraJqlOverride",  p.getJiraJqlOverride());
            m.put("jiraCrFilter",     p.getJiraCrFilter());
            m.put("jiraBugFilter",    p.getJiraBugFilter());
            m.put("isSharedProdBugs", p.isSharedProdBugs());
            m.put("clientCodeField",  p.getClientCodeField());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * POST /api/v1/projects/{id}/sync
     *
     * Per-project on-demand sync. PJM/HEAD_PJM/ADMIN may trigger delta syncs.
     * Full sync is restricted to HEAD_PJM and ADMIN.
     * Body: { "type": "delta" | "full" }  — type defaults to "delta" if omitted.
     */
    @PostMapping("/{id}/sync")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> syncProject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication auth) {

        String requested = body != null ? (String) body.getOrDefault("type", "delta") : "delta";

        // Only HEAD_PJM and ADMIN may run a full sync
        boolean canFull = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_PM")
                       || a.getAuthority().equals("ROLE_ADMIN"));
        String type = ("full".equalsIgnoreCase(requested) && canFull) ? "full" : "delta";

        return projects.findById(id)
            .filter(p -> Boolean.TRUE.equals(p.getActive()))
            .map(p -> ResponseEntity.ok(jiraSyncService.trigger(type, id)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/jira-config")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public ResponseEntity<?> updateJiraConfig(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        return projects.findById(id).map(p -> {
            if (body.containsKey("jiraProjectKeys")) p.setJiraProjectKeys((String) body.get("jiraProjectKeys"));
            if (body.containsKey("jiraJqlOverride")) p.setJiraJqlOverride((String) body.get("jiraJqlOverride"));
            if (body.containsKey("jiraCrFilter"))    p.setJiraCrFilter((String) body.get("jiraCrFilter"));
            if (body.containsKey("jiraBugFilter"))   p.setJiraBugFilter((String) body.get("jiraBugFilter"));
            projects.save(p);
            return ResponseEntity.ok(Map.of("ok", true));
        }).orElse(ResponseEntity.notFound().build());
    }
}
