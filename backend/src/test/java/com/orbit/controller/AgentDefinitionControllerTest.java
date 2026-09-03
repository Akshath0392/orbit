package com.orbit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.agent.AgentDefinition;
import com.orbit.domain.agent.AgentRun;
import com.orbit.domain.agent.AgentToolCall;
import com.orbit.repository.AgentDefinitionRepository;
import com.orbit.repository.AgentRunRepository;
import com.orbit.repository.AgentToolCallRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.security.JwtService;
import com.orbit.service.agent.AgentRuntime;
import com.orbit.service.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = AgentDefinitionController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class AgentDefinitionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean JwtService                jwtService;
    @MockBean AgentDefinitionRepository agentDefs;
    @MockBean AgentRunRepository        agentRuns;
    @MockBean AgentToolCallRepository   agentToolCalls;
    @MockBean AgentRuntime              agentRuntime;
    @MockBean ToolRegistry              toolRegistry;
    @MockBean ProjectRepository         projects;

    // ── helpers ───────────────────────────────────────────────────────────────

    private AgentDefinition def(Long id, String name, boolean system, boolean enabled) {
        AgentDefinition d = new AgentDefinition();
        d.setName(name); d.setAgentType("INTELLIGENCE"); d.setTriggerType("MANUAL");
        d.setEnabled(enabled); d.setSystemAgent(system); d.setRequiresHitl(true);
        d.setTools(new String[]{"memory.read"});
        d.setCreatedAt(LocalDateTime.now()); d.setUpdatedAt(LocalDateTime.now());
        try { var f = AgentDefinition.class.getDeclaredField("id"); f.setAccessible(true); f.set(d, id); }
        catch (Exception ignored) {}
        return d;
    }

    private AgentRun run(Long id, String status) {
        AgentRun r = new AgentRun();
        r.setStatus(status); r.setTriggeredBy("MANUAL_TEST"); r.setDurationMs(42);
        r.setStartedAt(LocalDateTime.now()); r.setCompletedAt(LocalDateTime.now());
        try { var f = AgentRun.class.getDeclaredField("id"); f.setAccessible(true); f.set(r, id); }
        catch (Exception ignored) {}
        return r;
    }

    private AgentToolCall toolCall(String toolName, boolean hitl, String result) {
        AgentToolCall tc = new AgentToolCall();
        tc.setToolName(toolName);
        tc.setHitlRequired(hitl);
        tc.setHitlOutcome(hitl ? "AWAITING_HITL" : null);
        tc.setResult(result);
        tc.setCalledAt(LocalDateTime.now());
        return tc;
    }

    // ── GET /agents — list ────────────────────────────────────────────────────

    @Test
    void listReturnsAllAgents() throws Exception {
        when(agentDefs.findAll()).thenReturn(List.of(
            def(1L, "DeliveryAgent", true, true),
            def(2L, "MyCustomAgent", false, false)
        ));

        mvc.perform(get("/api/v1/admin/agents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("DeliveryAgent"))
            .andExpect(jsonPath("$[1].name").value("MyCustomAgent"));
    }

    @Test
    void listIncludesSystemAgentFlag() throws Exception {
        when(agentDefs.findAll()).thenReturn(List.of(def(1L, "SysAgent", true, true)));

        mvc.perform(get("/api/v1/admin/agents"))
            .andExpect(jsonPath("$[0].systemAgent").value(true))
            .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    void listReturnsToolsAsArray() throws Exception {
        when(agentDefs.findAll()).thenReturn(List.of(def(1L, "A", false, true)));

        mvc.perform(get("/api/v1/admin/agents"))
            .andExpect(jsonPath("$[0].tools").isArray())
            .andExpect(jsonPath("$[0].tools[0]").value("memory.read"));
    }

    @Test
    void listIncludesPromptTemplateAndChannelConfig() throws Exception {
        AgentDefinition d = def(1L, "Agent", false, true);
        d.setPromptTemplate("You are an agent for {{project}}");
        d.setChannelConfig("{\"webhook\":\"https://hooks.slack.com/test\"}");
        when(agentDefs.findAll()).thenReturn(List.of(d));

        mvc.perform(get("/api/v1/admin/agents"))
            .andExpect(jsonPath("$[0].promptTemplate").value("You are an agent for {{project}}"))
            .andExpect(jsonPath("$[0].channelConfig").value("{\"webhook\":\"https://hooks.slack.com/test\"}"));
    }

    // ── POST /agents — create ─────────────────────────────────────────────────

    @Test
    void createSavesAgentAndReturnsId() throws Exception {
        AgentDefinition saved = def(10L, "MyAgent", false, false);
        when(agentDefs.save(any())).thenReturn(saved);

        mvc.perform(post("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "name", "MyAgent", "agentType", "REMINDER",
                    "triggerType", "CRON", "tools", List.of("memory.read")
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(10));
    }

    // ── PUT /agents/{id} — update ─────────────────────────────────────────────

    @Test
    void updateReturnsOkWhenFound() throws Exception {
        when(agentDefs.findById(1L)).thenReturn(Optional.of(def(1L, "Old", false, true)));
        when(agentDefs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(put("/api/v1/admin/agents/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Updated"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void updateReturns404WhenNotFound() throws Exception {
        when(agentDefs.findById(999L)).thenReturn(Optional.empty());

        mvc.perform(put("/api/v1/admin/agents/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "x"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void updatePersistsPromptTemplate() throws Exception {
        AgentDefinition d = def(1L, "Agent", false, true);
        when(agentDefs.findById(1L)).thenReturn(Optional.of(d));
        when(agentDefs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(put("/api/v1/admin/agents/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("promptTemplate", "Hello {{project}}"))))
            .andExpect(status().isOk());

        verify(agentDefs).save(argThat(saved ->
            "Hello {{project}}".equals(((AgentDefinition) saved).getPromptTemplate())));
    }

    // ── PATCH /agents/{id}/toggle ─────────────────────────────────────────────

    @Test
    void toggleFlipsEnabledFromTrueToFalse() throws Exception {
        when(agentDefs.findById(1L)).thenReturn(Optional.of(def(1L, "Agent", false, true)));
        when(agentDefs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(patch("/api/v1/admin/agents/1/toggle"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void toggleFlipsEnabledFromFalseToTrue() throws Exception {
        when(agentDefs.findById(1L)).thenReturn(Optional.of(def(1L, "Agent", false, false)));
        when(agentDefs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(patch("/api/v1/admin/agents/1/toggle"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void toggleReturns404WhenNotFound() throws Exception {
        when(agentDefs.findById(999L)).thenReturn(Optional.empty());

        mvc.perform(patch("/api/v1/admin/agents/999/toggle"))
            .andExpect(status().isNotFound());
    }

    // ── DELETE /agents/{id} ────────────────────────────────────────────────────

    @Test
    void deleteCustomAgentReturns204() throws Exception {
        when(agentDefs.findById(2L)).thenReturn(Optional.of(def(2L, "Custom", false, true)));

        mvc.perform(delete("/api/v1/admin/agents/2"))
            .andExpect(status().isNoContent());

        verify(agentDefs).delete(any());
    }

    @Test
    void deleteSystemAgentReturns400() throws Exception {
        when(agentDefs.findById(1L)).thenReturn(Optional.of(def(1L, "SysAgent", true, true)));

        mvc.perform(delete("/api/v1/admin/agents/1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        verify(agentDefs, never()).delete(any());
    }

    @Test
    void deleteReturns404WhenNotFound() throws Exception {
        when(agentDefs.findById(999L)).thenReturn(Optional.empty());

        mvc.perform(delete("/api/v1/admin/agents/999"))
            .andExpect(status().isNotFound());
    }

    // ── POST /agents/{id}/test-run ────────────────────────────────────────────

    @Test
    void testRunReturnsRunIdAndStatus() throws Exception {
        AgentDefinition d = def(1L, "Agent", false, true);
        AgentRun r = run(42L, "COMPLETED");
        when(agentDefs.findById(1L)).thenReturn(Optional.of(d));
        when(agentRuntime.execute(any(), any(), eq("MANUAL_TEST"), any())).thenReturn(r);
        when(agentToolCalls.findByRunId(42L)).thenReturn(List.of());

        mvc.perform(post("/api/v1/admin/agents/1/test-run"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value(42))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.durationMs").value(42));
    }

    @Test
    void testRunReturnsStepsArray() throws Exception {
        AgentDefinition d = def(1L, "Agent", false, true);
        AgentRun r = run(42L, "COMPLETED");
        when(agentDefs.findById(1L)).thenReturn(Optional.of(d));
        when(agentRuntime.execute(any(), any(), eq("MANUAL_TEST"), any())).thenReturn(r);
        when(agentToolCalls.findByRunId(42L)).thenReturn(List.of(
            toolCall("memory.read", false, "{\"result\":\"ok\"}")
        ));

        mvc.perform(post("/api/v1/admin/agents/1/test-run"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.steps").isArray())
            .andExpect(jsonPath("$.steps.length()").value(1));
    }

    @Test
    void testRunStepForNonHitlToolShowsExecutedStatus() throws Exception {
        AgentDefinition d = def(1L, "Agent", false, true);
        AgentRun r = run(42L, "COMPLETED");
        when(agentDefs.findById(1L)).thenReturn(Optional.of(d));
        when(agentRuntime.execute(any(), any(), eq("MANUAL_TEST"), any())).thenReturn(r);
        when(agentToolCalls.findByRunId(42L)).thenReturn(List.of(
            toolCall("orbit.get_cr_summary", false, "{\"totalCrs\":50}")
        ));

        mvc.perform(post("/api/v1/admin/agents/1/test-run"))
            .andExpect(jsonPath("$.steps[0].tool").value("orbit.get_cr_summary"))
            .andExpect(jsonPath("$.steps[0].status").value("EXECUTED"))
            .andExpect(jsonPath("$.steps[0].hitlRequired").value(false))
            .andExpect(jsonPath("$.steps[0].result").value("{\"totalCrs\":50}"));
    }

    @Test
    void testRunStepForHitlToolShowsAwaitingHitlStatus() throws Exception {
        AgentDefinition d = def(1L, "Agent", false, true);
        AgentRun r = run(42L, "COMPLETED");
        when(agentDefs.findById(1L)).thenReturn(Optional.of(d));
        when(agentRuntime.execute(any(), any(), eq("MANUAL_TEST"), any())).thenReturn(r);
        when(agentToolCalls.findByRunId(42L)).thenReturn(List.of(
            toolCall("slack.send_channel", true, "{\"status\":\"awaiting_hitl\"}")
        ));

        mvc.perform(post("/api/v1/admin/agents/1/test-run"))
            .andExpect(jsonPath("$.steps[0].tool").value("slack.send_channel"))
            .andExpect(jsonPath("$.steps[0].status").value("AWAITING_HITL"))
            .andExpect(jsonPath("$.steps[0].hitlRequired").value(true));
    }

    @Test
    void testRunWithMixedToolsReturnsAllSteps() throws Exception {
        AgentDefinition d = def(1L, "StandupAgent", false, true);
        AgentRun r = run(42L, "COMPLETED");
        when(agentDefs.findById(1L)).thenReturn(Optional.of(d));
        when(agentRuntime.execute(any(), any(), eq("MANUAL_TEST"), any())).thenReturn(r);
        when(agentToolCalls.findByRunId(42L)).thenReturn(List.of(
            toolCall("orbit.get_cr_summary", false, "{\"totalCrs\":100}"),
            toolCall("slack.send_channel",   false, "{\"sent\":true,\"channel\":\"#orbit\"}")
        ));

        mvc.perform(post("/api/v1/admin/agents/1/test-run"))
            .andExpect(jsonPath("$.steps.length()").value(2))
            .andExpect(jsonPath("$.steps[0].tool").value("orbit.get_cr_summary"))
            .andExpect(jsonPath("$.steps[0].status").value("EXECUTED"))
            .andExpect(jsonPath("$.steps[1].tool").value("slack.send_channel"))
            .andExpect(jsonPath("$.steps[1].status").value("EXECUTED"))
            .andExpect(jsonPath("$.steps[1].result").value("{\"sent\":true,\"channel\":\"#orbit\"}"));
    }

    @Test
    void testRunWithNoToolsReturnsEmptySteps() throws Exception {
        AgentDefinition d = def(1L, "Agent", false, true);
        AgentRun r = run(42L, "COMPLETED");
        when(agentDefs.findById(1L)).thenReturn(Optional.of(d));
        when(agentRuntime.execute(any(), any(), eq("MANUAL_TEST"), any())).thenReturn(r);
        when(agentToolCalls.findByRunId(42L)).thenReturn(List.of());

        mvc.perform(post("/api/v1/admin/agents/1/test-run"))
            .andExpect(jsonPath("$.steps").isArray())
            .andExpect(jsonPath("$.steps.length()").value(0));
    }

    @Test
    void testRunPassesDryRunInInputContext() throws Exception {
        AgentDefinition d = def(1L, "Agent", false, true);
        AgentRun r = run(1L, "COMPLETED");
        when(agentDefs.findById(1L)).thenReturn(Optional.of(d));
        when(agentRuntime.execute(any(), any(), eq("MANUAL_TEST"), any())).thenReturn(r);
        when(agentToolCalls.findByRunId(anyLong())).thenReturn(List.of());

        mvc.perform(post("/api/v1/admin/agents/1/test-run"))
            .andExpect(status().isOk());

        verify(agentRuntime).execute(any(), isNull(), eq("MANUAL_TEST"),
            argThat(ctx -> Boolean.TRUE.equals(ctx.get("dryRun"))));
    }

    @Test
    void testRunReturns404WhenAgentNotFound() throws Exception {
        when(agentDefs.findById(999L)).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/admin/agents/999/test-run"))
            .andExpect(status().isNotFound());
    }

    @Test
    void testRunWithFailedRunReturnsErrorMessage() throws Exception {
        AgentDefinition d = def(1L, "Agent", false, true);
        AgentRun r = run(42L, "FAILED");
        r.setErrorMessage("Connection refused");
        when(agentDefs.findById(1L)).thenReturn(Optional.of(d));
        when(agentRuntime.execute(any(), any(), eq("MANUAL_TEST"), any())).thenReturn(r);
        when(agentToolCalls.findByRunId(42L)).thenReturn(List.of());

        mvc.perform(post("/api/v1/admin/agents/1/test-run"))
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.errorMessage").value("Connection refused"));
    }

    // ── GET /agents/{id}/runs/{runId}/steps ───────────────────────────────────

    @Test
    void getRunStepsReturnsToolCallList() throws Exception {
        when(agentDefs.existsById(1L)).thenReturn(true);
        when(agentToolCalls.findByRunId(10L)).thenReturn(List.of(
            toolCall("orbit.get_cr_summary", false, "{\"totalCrs\":42}"),
            toolCall("slack.send_channel",   false, "{\"sent\":true}")
        ));

        mvc.perform(get("/api/v1/admin/agents/1/runs/10/steps"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].tool").value("orbit.get_cr_summary"))
            .andExpect(jsonPath("$[0].status").value("EXECUTED"))
            .andExpect(jsonPath("$[1].tool").value("slack.send_channel"))
            .andExpect(jsonPath("$[1].result").value("{\"sent\":true}"));
    }

    @Test
    void getRunStepsShowsHitlStatus() throws Exception {
        when(agentDefs.existsById(1L)).thenReturn(true);
        when(agentToolCalls.findByRunId(10L)).thenReturn(List.of(
            toolCall("email.send", true, "{\"status\":\"awaiting_hitl\"}")
        ));

        mvc.perform(get("/api/v1/admin/agents/1/runs/10/steps"))
            .andExpect(jsonPath("$[0].status").value("AWAITING_HITL"))
            .andExpect(jsonPath("$[0].hitlRequired").value(true));
    }

    @Test
    void getRunStepsReturns404WhenAgentMissing() throws Exception {
        when(agentDefs.existsById(999L)).thenReturn(false);

        mvc.perform(get("/api/v1/admin/agents/999/runs/1/steps"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getRunStepsReturnsEmptyListWhenNoToolCalls() throws Exception {
        when(agentDefs.existsById(1L)).thenReturn(true);
        when(agentToolCalls.findByRunId(5L)).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/agents/1/runs/5/steps"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ── GET /agents/{id}/runs ─────────────────────────────────────────────────

    @Test
    void runHistoryReturnsPaginatedRuns() throws Exception {
        when(agentDefs.existsById(1L)).thenReturn(true);
        when(agentRuns.findByAgentIdOrderByStartedAtDesc(eq(1L), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(run(10L, "COMPLETED"), run(11L, "FAILED"))));

        mvc.perform(get("/api/v1/admin/agents/1/runs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.content[1].status").value("FAILED"));
    }

    @Test
    void runHistoryReturns404WhenAgentMissing() throws Exception {
        when(agentDefs.existsById(999L)).thenReturn(false);

        mvc.perform(get("/api/v1/admin/agents/999/runs"))
            .andExpect(status().isNotFound());
    }

    // ── GET /agents/tools ─────────────────────────────────────────────────────

    @Test
    void listToolsReturnsRegisteredTools() throws Exception {
        when(toolRegistry.listAll()).thenReturn(List.of(
            Map.of("id", "memory.read",        "description", "Read agent memory", "requiresHitl", false),
            Map.of("id", "slack.send_channel",  "description", "Post to Slack",     "requiresHitl", false)
        ));

        mvc.perform(get("/api/v1/admin/agents/tools"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("memory.read"))
            .andExpect(jsonPath("$[0].requiresHitl").value(false))
            .andExpect(jsonPath("$[1].id").value("slack.send_channel"))
            .andExpect(jsonPath("$[1].requiresHitl").value(false));
    }
}
