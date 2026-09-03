package com.orbit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.client.Client;
import com.orbit.domain.report.GeneratedReport;
import com.orbit.repository.*;
import com.orbit.service.agent.AgentInvocationService;
import com.orbit.service.agent.ReportDraftingAgent;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final GeneratedReportRepository reports;
    private final ClientRepository clients;
    private final ReportDraftingAgent reportDraftingAgent;
    private final AgentInvocationService agentInvocations;
    private final ObjectMapper mapper;

    public ReportController(GeneratedReportRepository reports, ClientRepository clients,
                             ReportDraftingAgent reportDraftingAgent,
                             AgentInvocationService agentInvocations,
                             ObjectMapper mapper) {
        this.reports = reports;
        this.clients = clients;
        this.reportDraftingAgent = reportDraftingAgent;
        this.agentInvocations = agentInvocations;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Page<Map<String,Object>> list(
            @RequestParam(required=false) Long clientId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return reports.findFiltered(clientId, PageRequest.of(page, size)).map(r -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("type", r.getType());
            m.put("client", r.getClient() != null ? r.getClient().getName() : "All clients");
            m.put("status", r.getStatus());
            m.put("generatedBy", r.getGeneratedBy());
            m.put("generatedAt", r.getGeneratedAt());
            return m;
        });
    }

    // Canonical report-type list — drives the "Generate report" modal and KPI tile.
    // Keeping this server-side ensures the frontend can't add unsupported types.
    private static final List<String> REPORT_TYPES = List.of(
        "Weekly delivery",
        "Daily snapshot",
        "Client backlog",
        "Executive summary",
        "Bug SLA report"
    );

    @GetMapping("/templates")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> templates() {
        return REPORT_TYPES.stream().map(t -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", t); m.put("name", t);
            return m;
        }).toList();
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String,Object> stats() {
        java.time.LocalDateTime weekAgo = java.time.LocalDateTime.now().minusDays(7);
        long generatedThisWeek = reports.countByGeneratedAtAfter(weekAgo);
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("reportTypes",       REPORT_TYPES.size());
        m.put("generatedThisWeek", generatedThisWeek);
        return m;
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> preview(@PathVariable Long id) {
        return reports.findById(id).map(r -> {
            try {
                Object content = r.getContentJson() != null
                    ? mapper.readValue(r.getContentJson(), Map.class) : Map.of();
                return ResponseEntity.ok(content);
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of("sections", List.of()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> generate(@RequestBody Map<String,Object> body, Authentication auth) {
        Client client = body.get("clientId") != null
            ? clients.findById(Long.valueOf(body.get("clientId").toString())).orElse(null) : null;
        GeneratedReport r = GeneratedReport.builder()
            .type((String) body.getOrDefault("type","Weekly delivery"))
            .client(client)
            .status("GENERATING")
            .generatedBy(auth.getName())
            .clientSafe(Boolean.TRUE.equals(body.get("clientSafeFilter")))
            .build();
        reports.save(r);
        Long reportId = r.getId();
        String userId = auth.getName();

        // Route through the unified agent invocation facade so the run carries
        // invocation_source=UI and shows up in agent audit/cost reporting.
        agentInvocations.invokeAs(userId, "report.draft",
            Map.of("reportId", reportId, "userId", userId), "UI");

        Map<String, Object> accepted = new LinkedHashMap<>();
        accepted.put("id", reportId);
        accepted.put("status", "GENERATING");
        return ResponseEntity.accepted().body(accepted);
    }
}
