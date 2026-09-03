package com.orbit.controller;

import com.orbit.domain.agent.AgentRun;
import com.orbit.domain.agent.AgentToolCall;
import com.orbit.repository.AgentDefinitionRepository;
import com.orbit.repository.AgentRunRepository;
import com.orbit.repository.AgentToolCallRepository;
import com.orbit.service.agent.HitlApprovalService;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/agents")
public class AgentLogsController {

    private final AgentRunRepository runRepo;
    private final AgentToolCallRepository toolCallRepo;
    private final AgentDefinitionRepository agentDefs;
    private final HitlApprovalService hitlService;

    public AgentLogsController(AgentRunRepository runRepo,
                                AgentToolCallRepository toolCallRepo,
                                AgentDefinitionRepository agentDefs,
                                HitlApprovalService hitlService) {
        this.runRepo = runRepo;
        this.toolCallRepo = toolCallRepo;
        this.agentDefs = agentDefs;
        this.hitlService = hitlService;
    }

    // ── Cross-agent run listing ────────────────────────────────────────────────

    @GetMapping("/runs")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public Page<Map<String, Object>> allRuns(
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return runRepo.findAllFiltered(agentId, status, PageRequest.of(page, size))
            .map(r -> enrichRun(r, true));
    }

    // ── Pending HITL inbox ─────────────────────────────────────────────────────

    @GetMapping("/runs/pending-hitl")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public List<Map<String, Object>> pendingHitl() {
        return toolCallRepo.findPendingHitl().stream()
            .map(tc -> {
                Map<String, Object> m = stepMap(tc);
                // Enrich with run + agent info
                runRepo.findById(tc.getRunId()).ifPresent(run -> {
                    m.put("runId", run.getId());
                    m.put("triggeredBy", run.getTriggeredBy());
                    m.put("runStartedAt", run.getStartedAt());
                    agentDefs.findById(run.getAgentId()).ifPresent(def -> {
                        m.put("agentId", def.getId());
                        m.put("agentName", def.getName());
                        m.put("agentType", def.getAgentType());
                    });
                });
                return m;
            })
            .collect(Collectors.toList());
    }

    // ── Run steps (tool call detail) ───────────────────────────────────────────

    @GetMapping("/runs/{runId}/steps")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public ResponseEntity<?> runSteps(@PathVariable Long runId) {
        if (!runRepo.existsById(runId)) return ResponseEntity.notFound().build();
        List<Map<String, Object>> steps = toolCallRepo.findByRunId(runId)
            .stream().map(this::stepMap).collect(Collectors.toList());
        return ResponseEntity.ok(steps);
    }

    // ── HITL approve ──────────────────────────────────────────────────────────

    @PostMapping("/runs/{runId}/steps/{stepId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public ResponseEntity<?> approve(@PathVariable Long runId,
                                     @PathVariable Long stepId,
                                     @RequestBody(required = false) Map<String, Object> body,
                                     Authentication auth) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> editedArgs = body != null
                ? (Map<String, Object>) body.get("editedArgs") : null;
            String decidedBy = auth != null ? auth.getName() : "system";
            Map<String, Object> result = hitlService.approve(runId, stepId, editedArgs, decidedBy);
            return ResponseEntity.ok(Map.of("ok", true, "result", result));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── HITL reject ───────────────────────────────────────────────────────────

    @PostMapping("/runs/{runId}/steps/{stepId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public ResponseEntity<?> reject(@PathVariable Long runId,
                                    @PathVariable Long stepId,
                                    @RequestBody Map<String, Object> body,
                                    Authentication auth) {
        String reason = (String) body.get("reason");
        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "reason is required"));
        }
        try {
            String decidedBy = auth != null ? auth.getName() : "system";
            hitlService.reject(runId, stepId, reason, decidedBy);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> enrichRun(AgentRun r, boolean includeHitlCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("agentId", r.getAgentId());
        m.put("triggeredBy", r.getTriggeredBy());
        m.put("invocationSource", r.getInvocationSource());
        m.put("status", r.getStatus());
        m.put("outputSummary", r.getOutputSummary());
        m.put("errorMessage", r.getErrorMessage());
        m.put("durationMs", r.getDurationMs());
        m.put("startedAt", r.getStartedAt());
        m.put("completedAt", r.getCompletedAt());

        // Resolve agent name
        agentDefs.findById(r.getAgentId()).ifPresent(def -> {
            m.put("agentName", def.getName());
            m.put("agentType", def.getAgentType());
        });

        if (includeHitlCount) {
            long pending = toolCallRepo.findByRunId(r.getId()).stream()
                .filter(tc -> "AWAITING_HITL".equals(tc.getHitlOutcome())).count();
            m.put("pendingHitl", pending);
        }
        return m;
    }

    private Map<String, Object> stepMap(AgentToolCall tc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", tc.getId());
        m.put("runId", tc.getRunId());
        m.put("tool", tc.getToolName());
        m.put("status", resolveStepStatus(tc));
        m.put("hitlRequired", tc.getHitlRequired());
        m.put("hitlOutcome", tc.getHitlOutcome());
        m.put("args", tc.getArgs());
        m.put("result", tc.getResult());
        m.put("hitlNote", tc.getHitlNote());
        m.put("calledAt", tc.getCalledAt());
        return m;
    }

    private String resolveStepStatus(AgentToolCall tc) {
        if ("AWAITING_HITL".equals(tc.getHitlOutcome())) return "AWAITING_HITL";
        if ("REJECTED".equals(tc.getHitlOutcome()))       return "REJECTED";
        if (tc.getResult() != null && tc.getResult().contains("\"error\"")) return "ERROR";
        if (Boolean.TRUE.equals(tc.getHitlRequired()))    return "APPROVED";
        return "EXECUTED";
    }
}
