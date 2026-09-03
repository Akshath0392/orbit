package com.orbit.controller;

import com.orbit.domain.agent.AgentDefinition;
import com.orbit.domain.agent.AgentRun;
import com.orbit.domain.agent.AgentToolCall;
import com.orbit.repository.AgentDefinitionRepository;
import com.orbit.repository.AgentRunRepository;
import com.orbit.repository.AgentToolCallRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.service.agent.AgentRuntime;
import com.orbit.service.agent.tool.ToolRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/agents")
public class AgentDefinitionController {

    private final AgentDefinitionRepository agentDefs;
    private final AgentRunRepository agentRuns;
    private final AgentToolCallRepository agentToolCalls;
    private final AgentRuntime agentRuntime;
    private final ToolRegistry toolRegistry;
    private final ProjectRepository projects;

    public AgentDefinitionController(AgentDefinitionRepository agentDefs,
                                     AgentRunRepository agentRuns,
                                     AgentToolCallRepository agentToolCalls,
                                     AgentRuntime agentRuntime,
                                     ToolRegistry toolRegistry,
                                     ProjectRepository projects) {
        this.agentDefs = agentDefs;
        this.agentRuns = agentRuns;
        this.agentToolCalls = agentToolCalls;
        this.agentRuntime = agentRuntime;
        this.toolRegistry = toolRegistry;
        this.projects = projects;
    }

    // ── List all agent definitions ────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> listAgents() {
        return agentDefs.findAll().stream()
            .map(this::toMap)
            .collect(Collectors.toList());
    }

    // ── Create a new agent definition ─────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createAgent(@RequestBody Map<String, Object> body) {
        AgentDefinition def = new AgentDefinition();
        applyBody(def, body);
        def.setCreatedAt(LocalDateTime.now());
        def.setUpdatedAt(LocalDateTime.now());
        AgentDefinition saved = agentDefs.save(def);
        return ResponseEntity.ok(Map.of("id", saved.getId()));
    }

    // ── Update an existing agent definition ───────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateAgent(@PathVariable Long id,
                                         @RequestBody Map<String, Object> body) {
        return agentDefs.findById(id).map(def -> {
            applyBody(def, body);
            def.setUpdatedAt(LocalDateTime.now());
            agentDefs.save(def);
            return ResponseEntity.ok(Map.of("ok", true));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Toggle enabled/disabled ───────────────────────────────────────────────

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleAgent(@PathVariable Long id) {
        return agentDefs.findById(id).map(def -> {
            def.setEnabled(!Boolean.TRUE.equals(def.getEnabled()));
            def.setUpdatedAt(LocalDateTime.now());
            agentDefs.save(def);
            return ResponseEntity.ok(Map.of("id", id, "enabled", def.getEnabled()));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Delete (not allowed for system agents) ────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteAgent(@PathVariable Long id) {
        return agentDefs.findById(id).map(def -> {
            if (Boolean.TRUE.equals(def.getSystemAgent())) {
                return ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "System agents cannot be deleted"));
            }
            agentDefs.delete(def);
            return ResponseEntity.noContent().<Object>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Test run ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/test-run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> testRun(@PathVariable Long id,
                                     @RequestParam(required = false) Long projectId) {
        return agentDefs.findById(id).map(def -> {
            AgentRun run = agentRuntime.execute(def, projectId, "MANUAL_TEST", Map.of("dryRun", true));

            List<Map<String, Object>> steps = agentToolCalls.findByRunId(run.getId())
                .stream().map(tc -> {
                    Map<String, Object> step = new LinkedHashMap<>();
                    step.put("tool", tc.getToolName());
                    step.put("status", Boolean.TRUE.equals(tc.getHitlRequired())
                        ? "AWAITING_HITL" : "EXECUTED");
                    step.put("hitlRequired", tc.getHitlRequired());
                    step.put("result", tc.getResult());
                    step.put("error", tc.getHitlNote());
                    return step;
                }).collect(Collectors.toList());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("runId", run.getId());
            result.put("status", run.getStatus());
            result.put("durationMs", run.getDurationMs());
            result.put("steps", steps);
            result.put("errorMessage", run.getErrorMessage());
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Run steps (tool calls) for a specific run ─────────────────────────────

    @GetMapping("/{id}/runs/{runId}/steps")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getRunSteps(@PathVariable Long id, @PathVariable Long runId) {
        if (!agentDefs.existsById(id)) return ResponseEntity.notFound().build();
        List<Map<String, Object>> steps = agentToolCalls.findByRunId(runId)
            .stream().map(tc -> {
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("tool", tc.getToolName());
                step.put("status", Boolean.TRUE.equals(tc.getHitlRequired()) ? "AWAITING_HITL" : "EXECUTED");
                step.put("hitlRequired", tc.getHitlRequired());
                step.put("result", tc.getResult());
                step.put("calledAt", tc.getCalledAt());
                return step;
            }).collect(Collectors.toList());
        return ResponseEntity.ok(steps);
    }

    // ── Paginated run history for an agent ───────────────────────────────────

    @GetMapping("/{id}/runs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getRunHistory(@PathVariable Long id,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        if (!agentDefs.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        Page<Map<String, Object>> result = agentRuns
            .findByAgentIdOrderByStartedAtDesc(id, PageRequest.of(page, size))
            .map(this::runToMap);
        return ResponseEntity.ok(result);
    }

    // ── List all available tools ──────────────────────────────────────────────

    @GetMapping("/tools")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> listTools() {
        return toolRegistry.listAll();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void applyBody(AgentDefinition def, Map<String, Object> body) {
        if (body.containsKey("name")) def.setName((String) body.get("name"));
        if (body.containsKey("description")) def.setDescription((String) body.get("description"));
        if (body.containsKey("agentType")) def.setAgentType((String) body.get("agentType"));
        if (body.containsKey("triggerType")) def.setTriggerType((String) body.get("triggerType"));
        if (body.containsKey("triggerConfig")) def.setTriggerConfig(body.get("triggerConfig").toString());
        if (body.containsKey("promptTemplate")) def.setPromptTemplate((String) body.get("promptTemplate"));
        if (body.containsKey("outputChannel")) def.setOutputChannel((String) body.get("outputChannel"));
        if (body.containsKey("channelConfig")) def.setChannelConfig(body.get("channelConfig").toString());
        if (body.containsKey("requiresHitl")) def.setRequiresHitl(Boolean.TRUE.equals(body.get("requiresHitl")));
        if (body.containsKey("enabled")) def.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        if (body.containsKey("projectId") && body.get("projectId") != null)
            def.setProjectId(Long.valueOf(body.get("projectId").toString()));
        if (body.containsKey("tools")) {
            Object toolsVal = body.get("tools");
            if (toolsVal instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> toolList = (List<String>) toolsVal;
                def.setTools(toolList.toArray(new String[0]));
            } else if (toolsVal instanceof String) {
                def.setToolsCsv((String) toolsVal);
            }
        }
    }

    private Map<String, Object> toMap(AgentDefinition def) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", def.getId());
        m.put("name", def.getName());
        m.put("description", def.getDescription() != null ? def.getDescription() : "");
        m.put("agentType", def.getAgentType());
        m.put("triggerType", def.getTriggerType());
        m.put("triggerConfig", def.getTriggerConfig() != null ? def.getTriggerConfig() : "{}");
        m.put("promptTemplate", def.getPromptTemplate() != null ? def.getPromptTemplate() : "");
        m.put("tools", def.getTools() != null ? Arrays.asList(def.getTools()) : List.of());
        m.put("outputChannel", def.getOutputChannel() != null ? def.getOutputChannel() : "");
        m.put("channelConfig", def.getChannelConfig() != null ? def.getChannelConfig() : "{}");
        m.put("requiresHitl", Boolean.TRUE.equals(def.getRequiresHitl()));
        m.put("enabled", Boolean.TRUE.equals(def.getEnabled()));
        m.put("systemAgent", Boolean.TRUE.equals(def.getSystemAgent()));
        m.put("projectId", def.getProjectId());
        m.put("createdBy", def.getCreatedBy() != null ? def.getCreatedBy() : "");
        m.put("createdAt", def.getCreatedAt() != null ? def.getCreatedAt().toString() : "");
        m.put("updatedAt", def.getUpdatedAt() != null ? def.getUpdatedAt().toString() : "");
        return m;
    }

    private Map<String, Object> runToMap(AgentRun run) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", run.getId());
        m.put("agentId", run.getAgentId());
        m.put("projectId", run.getProjectId());
        m.put("triggeredBy", run.getTriggeredBy() != null ? run.getTriggeredBy() : "");
        m.put("status", run.getStatus());
        m.put("outputSummary", run.getOutputSummary() != null ? run.getOutputSummary() : "");
        m.put("errorMessage", run.getErrorMessage() != null ? run.getErrorMessage() : "");
        m.put("tokensUsed", run.getTokensUsed() != null ? run.getTokensUsed() : 0);
        m.put("durationMs", run.getDurationMs() != null ? run.getDurationMs() : 0);
        m.put("startedAt", run.getStartedAt() != null ? run.getStartedAt().toString() : "");
        m.put("completedAt", run.getCompletedAt() != null ? run.getCompletedAt().toString() : "");
        return m;
    }
}
