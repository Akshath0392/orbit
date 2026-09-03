package com.orbit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.agent.AgentRun;
import com.orbit.domain.agent.AgentToolCall;
import com.orbit.repository.AgentDefinitionRepository;
import com.orbit.repository.AgentRunRepository;
import com.orbit.repository.AgentToolCallRepository;
import com.orbit.security.JwtService;
import com.orbit.service.agent.HitlApprovalService;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = AgentLogsController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class AgentLogsControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean JwtService                jwtService;
    @MockBean AgentRunRepository        runRepo;
    @MockBean AgentToolCallRepository   toolCallRepo;
    @MockBean AgentDefinitionRepository agentDefs;
    @MockBean HitlApprovalService       hitlService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AgentRun run(Long id, Long agentId, String status) {
        AgentRun r = new AgentRun();
        r.setAgentId(agentId); r.setTriggeredBy("MANUAL_TEST");
        r.setStatus(status); r.setDurationMs(42);
        r.setStartedAt(LocalDateTime.now()); r.setCompletedAt(LocalDateTime.now());
        try { var f = AgentRun.class.getDeclaredField("id"); f.setAccessible(true); f.set(r, id); }
        catch (Exception ignored) {}
        return r;
    }

    private AgentToolCall step(Long id, Long runId, String tool, String hitlOutcome, boolean hitlRequired) {
        AgentToolCall tc = new AgentToolCall();
        tc.setRunId(runId); tc.setToolName(tool);
        tc.setHitlRequired(hitlRequired); tc.setHitlOutcome(hitlOutcome);
        tc.setArgs(hitlRequired ? "{\"channel\":\"#orbit\"}" : null);
        tc.setResult("{\"ok\":true}"); tc.setCalledAt(LocalDateTime.now());
        try { var f = AgentToolCall.class.getDeclaredField("id"); f.setAccessible(true); f.set(tc, id); }
        catch (Exception ignored) {}
        return tc;
    }

    // ── GET /runs — cross-agent listing ───────────────────────────────────────

    @Test
    void allRunsReturnsPaginatedList() throws Exception {
        when(runRepo.findAllFiltered(isNull(), isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(run(1L, 2L, "COMPLETED"), run(2L, 3L, "FAILED"))));
        when(agentDefs.findById(anyLong())).thenReturn(Optional.empty());
        when(toolCallRepo.findByRunId(anyLong())).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/agents/runs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.content[1].status").value("FAILED"));
    }

    @Test
    void allRunsIncludesPendingHitlCount() throws Exception {
        AgentRun r = run(5L, 1L, "COMPLETED");
        when(runRepo.findAllFiltered(isNull(), isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(r)));
        when(agentDefs.findById(anyLong())).thenReturn(Optional.empty());
        when(toolCallRepo.findByRunId(5L)).thenReturn(List.of(
            step(10L, 5L, "slack.send_channel", "AWAITING_HITL", true),
            step(11L, 5L, "orbit.get_cr_summary", null, false)
        ));

        mvc.perform(get("/api/v1/admin/agents/runs"))
            .andExpect(jsonPath("$.content[0].pendingHitl").value(1));
    }

    @Test
    void allRunsResolvesAgentName() throws Exception {
        AgentRun r = run(1L, 2L, "COMPLETED");
        when(runRepo.findAllFiltered(isNull(), isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(r)));
        when(toolCallRepo.findByRunId(anyLong())).thenReturn(List.of());

        var def = new com.orbit.domain.agent.AgentDefinition();
        def.setName("StandupAgent"); def.setAgentType("COMMUNICATION");
        when(agentDefs.findById(2L)).thenReturn(Optional.of(def));

        mvc.perform(get("/api/v1/admin/agents/runs"))
            .andExpect(jsonPath("$.content[0].agentName").value("StandupAgent"))
            .andExpect(jsonPath("$.content[0].agentType").value("COMMUNICATION"));
    }

    @Test
    void allRunsFiltersbyAgentId() throws Exception {
        when(runRepo.findAllFiltered(eq(2L), isNull(), org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(run(1L, 2L, "COMPLETED"))));
        when(agentDefs.findById(anyLong())).thenReturn(Optional.empty());
        when(toolCallRepo.findByRunId(anyLong())).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/agents/runs?agentId=2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1));

        verify(runRepo).findAllFiltered(eq(2L), isNull(), org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void allRunsFiltersByStatus() throws Exception {
        when(runRepo.findAllFiltered(isNull(), eq("FAILED"), org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(run(3L, 1L, "FAILED"))));
        when(agentDefs.findById(anyLong())).thenReturn(Optional.empty());
        when(toolCallRepo.findByRunId(anyLong())).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/agents/runs?status=FAILED"))
            .andExpect(jsonPath("$.content[0].status").value("FAILED"));
    }

    // ── GET /runs/pending-hitl ────────────────────────────────────────────────

    @Test
    void pendingHitlReturnsAwaitingItems() throws Exception {
        when(toolCallRepo.findPendingHitl()).thenReturn(List.of(
            step(10L, 5L, "email.send", "AWAITING_HITL", true)
        ));
        when(runRepo.findById(5L)).thenReturn(Optional.of(run(5L, 2L, "COMPLETED")));
        when(agentDefs.findById(2L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/admin/agents/runs/pending-hitl"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].tool").value("email.send"))
            .andExpect(jsonPath("$[0].status").value("AWAITING_HITL"))
            .andExpect(jsonPath("$[0].runId").value(5))
            .andExpect(jsonPath("$[0].hitlRequired").value(true));
    }

    @Test
    void pendingHitlEnrichesWithAgentName() throws Exception {
        when(toolCallRepo.findPendingHitl()).thenReturn(List.of(
            step(10L, 5L, "email.send", "AWAITING_HITL", true)
        ));
        when(runRepo.findById(5L)).thenReturn(Optional.of(run(5L, 2L, "COMPLETED")));

        var def = new com.orbit.domain.agent.AgentDefinition();
        def.setName("EscalationAgent"); def.setAgentType("ESCALATION");
        when(agentDefs.findById(2L)).thenReturn(Optional.of(def));

        mvc.perform(get("/api/v1/admin/agents/runs/pending-hitl"))
            .andExpect(jsonPath("$[0].agentName").value("EscalationAgent"))
            .andExpect(jsonPath("$[0].agentType").value("ESCALATION"));
    }

    @Test
    void pendingHitlReturnsEmptyWhenNonePending() throws Exception {
        when(toolCallRepo.findPendingHitl()).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/agents/runs/pending-hitl"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    // ── GET /runs/{runId}/steps ───────────────────────────────────────────────

    @Test
    void runStepsReturnsToolCallList() throws Exception {
        when(runRepo.existsById(5L)).thenReturn(true);
        when(toolCallRepo.findByRunId(5L)).thenReturn(List.of(
            step(10L, 5L, "orbit.get_cr_summary", null, false),
            step(11L, 5L, "slack.send_channel",   null, false)
        ));

        mvc.perform(get("/api/v1/admin/agents/runs/5/steps"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].tool").value("orbit.get_cr_summary"))
            .andExpect(jsonPath("$[0].status").value("EXECUTED"))
            .andExpect(jsonPath("$[1].tool").value("slack.send_channel"));
    }

    @Test
    void runStepsShowsAwaitingHitlStatus() throws Exception {
        when(runRepo.existsById(5L)).thenReturn(true);
        when(toolCallRepo.findByRunId(5L)).thenReturn(List.of(
            step(12L, 5L, "email.send", "AWAITING_HITL", true)
        ));

        mvc.perform(get("/api/v1/admin/agents/runs/5/steps"))
            .andExpect(jsonPath("$[0].status").value("AWAITING_HITL"))
            .andExpect(jsonPath("$[0].hitlRequired").value(true));
    }

    @Test
    void runStepsShowsRejectedStatus() throws Exception {
        when(runRepo.existsById(5L)).thenReturn(true);
        when(toolCallRepo.findByRunId(5L)).thenReturn(List.of(
            step(13L, 5L, "email.send", "REJECTED", true)
        ));

        mvc.perform(get("/api/v1/admin/agents/runs/5/steps"))
            .andExpect(jsonPath("$[0].status").value("REJECTED"));
    }

    @Test
    void runStepsReturns404WhenRunMissing() throws Exception {
        when(runRepo.existsById(999L)).thenReturn(false);

        mvc.perform(get("/api/v1/admin/agents/runs/999/steps"))
            .andExpect(status().isNotFound());
    }

    // ── POST /runs/{runId}/steps/{stepId}/approve ─────────────────────────────

    @Test
    void approveCallsHitlServiceAndReturnsResult() throws Exception {
        when(hitlService.approve(eq(5L), eq(10L), isNull(), anyString()))
            .thenReturn(Map.of("ok", true, "ts", "178196.001"));

        mvc.perform(post("/api/v1/admin/agents/runs/5/steps/10/approve")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.result.ok").value(true))
            .andExpect(jsonPath("$.result.ts").value("178196.001"));

        verify(hitlService).approve(eq(5L), eq(10L), isNull(), anyString());
    }

    @Test
    void approveWithEditedArgsForwardsThemToService() throws Exception {
        when(hitlService.approve(eq(5L), eq(10L), any(), anyString()))
            .thenReturn(Map.of("ok", true));

        mvc.perform(post("/api/v1/admin/agents/runs/5/steps/10/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "editedArgs", Map.of("channel", "#new-channel", "message", "Updated msg")
                ))))
            .andExpect(status().isOk());

        verify(hitlService).approve(eq(5L), eq(10L),
            argThat(args -> "#new-channel".equals(((Map<?,?>) args).get("channel"))),
            anyString());
    }

    @Test
    void approveReturns400WhenStepAlreadyActioned() throws Exception {
        when(hitlService.approve(eq(5L), eq(10L), isNull(), anyString()))
            .thenThrow(new IllegalStateException("Step is not awaiting HITL — current state: APPROVED"));

        mvc.perform(post("/api/v1/admin/agents/runs/5/steps/10/approve")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(containsString("not awaiting HITL")));
    }

    @Test
    void approveReturns404WhenStepMissing() throws Exception {
        when(hitlService.approve(eq(5L), eq(999L), isNull(), anyString()))
            .thenThrow(new IllegalArgumentException("Step not found: 999"));

        mvc.perform(post("/api/v1/admin/agents/runs/5/steps/999/approve")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
    }

    // ── POST /runs/{runId}/steps/{stepId}/reject ──────────────────────────────

    @Test
    void rejectCallsHitlServiceAndReturnsOk() throws Exception {
        doNothing().when(hitlService).reject(eq(5L), eq(10L), eq("Wrong recipient"), anyString());

        mvc.perform(post("/api/v1/admin/agents/runs/5/steps/10/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("reason", "Wrong recipient"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));

        verify(hitlService).reject(eq(5L), eq(10L), eq("Wrong recipient"), anyString());
    }

    @Test
    void rejectReturns400WhenReasonMissing() throws Exception {
        mvc.perform(post("/api/v1/admin/agents/runs/5/steps/10/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("reason is required"));

        verify(hitlService, never()).reject(anyLong(), anyLong(), any(), any());
    }

    @Test
    void rejectReturns400WhenReasonBlank() throws Exception {
        mvc.perform(post("/api/v1/admin/agents/runs/5/steps/10/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("reason", "   "))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("reason is required"));
    }

    @Test
    void rejectReturns400WhenStepAlreadyActioned() throws Exception {
        doThrow(new IllegalStateException("Step is not awaiting HITL — current state: REJECTED"))
            .when(hitlService).reject(eq(5L), eq(10L), any(), anyString());

        mvc.perform(post("/api/v1/admin/agents/runs/5/steps/10/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("reason", "Duplicate"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(containsString("not awaiting HITL")));
    }

    @Test
    void rejectReturns404WhenStepMissing() throws Exception {
        doThrow(new IllegalArgumentException("Step not found: 999"))
            .when(hitlService).reject(eq(5L), eq(999L), any(), anyString());

        mvc.perform(post("/api/v1/admin/agents/runs/5/steps/999/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("reason", "test"))))
            .andExpect(status().isNotFound());
    }
}
