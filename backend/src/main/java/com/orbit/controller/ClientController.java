package com.orbit.controller;

import com.orbit.domain.client.*;
import com.orbit.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientRepository clients;
    private final ClientDependencyRepository deps;
    private final com.orbit.service.client.ClientOverviewService overview;

    public ClientController(ClientRepository clients, ClientDependencyRepository deps,
            com.orbit.service.client.ClientOverviewService overview) {
        this.clients = clients; this.deps = deps; this.overview = overview;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public List<Map<String,Object>> list() {
        return overview.clientOverviews();
    }

    @GetMapping("/{id}/dependencies")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public List<Map<String,Object>> getDeps(@PathVariable Long id) {
        return deps.findByClientIdOrderByRaisedAtDesc(id).stream().map(d -> {
            long days = d.getRaisedAt() != null
                ? java.time.temporal.ChronoUnit.DAYS.between(d.getRaisedAt(), LocalDate.now()) : 0;
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",          d.getId());
            m.put("title",       d.getTitle());
            m.put("description", d.getDescription() != null ? d.getDescription() : "");
            m.put("depType",     d.getDepType() != null ? d.getDepType() : "CLIENT");
            m.put("age",         days + "d");
            m.put("status",      d.getStatus());
            return m;
        }).collect(Collectors.toList());
    }

    @PostMapping("/{id}/dependencies")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> addDep(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        return clients.findById(id).map(c -> {
            ClientDependency d = ClientDependency.builder()
                .client(c)
                .title((String) body.get("title"))
                .description((String) body.getOrDefault("description",""))
                .depType((String) body.getOrDefault("depType","CLIENT"))
                .raisedAt(LocalDate.now())
                .status("OPEN")
                .build();
            deps.save(d);
            Map<String,Object> r = new LinkedHashMap<>();
            r.put("id", d.getId()); r.put("title", d.getTitle());
            return ResponseEntity.ok(r);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/thresholds")
    @PreAuthorize("hasRole('ADMIN')")
    @com.orbit.config.EvictsDashboardCaches
    public ResponseEntity<?> updateThresholds(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        return clients.findById(id).map(c -> {
            if (body.get("healthGreenThreshold") != null)
                c.setHealthGreenThreshold(Integer.parseInt(body.get("healthGreenThreshold").toString()));
            if (body.get("healthAmberThreshold") != null)
                c.setHealthAmberThreshold(Integer.parseInt(body.get("healthAmberThreshold").toString()));
            clients.save(c);
            Map<String,Object> r = new LinkedHashMap<>();
            r.put("id", c.getId()); r.put("updated", true);
            return ResponseEntity.ok(r);
        }).orElse(ResponseEntity.notFound().build());
    }

}
