package com.orbit.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.agent.AgentDecisionLog;
import com.orbit.domain.agent.AgentRun;
import com.orbit.domain.agent.AgentToolCall;
import com.orbit.repository.AgentDecisionLogRepository;
import com.orbit.repository.AgentRunRepository;
import com.orbit.repository.AgentToolCallRepository;
import com.orbit.service.agent.HitlApprovalService;
import com.orbit.service.agent.tool.AgentRunContext;
import com.orbit.service.agent.tool.AgentTool;
import com.orbit.service.agent.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HitlApprovalServiceTest {

    AgentToolCallRepository toolCallRepo;
    AgentRunRepository      runRepo;
    AgentDecisionLogRepository decisionRepo;
    ToolRegistry            toolRegistry;
    HitlApprovalService     service;
    ObjectMapper            om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        toolCallRepo  = mock(AgentToolCallRepository.class);
        runRepo       = mock(AgentRunRepository.class);
        decisionRepo  = mock(AgentDecisionLogRepository.class);
        toolRegistry  = mock(ToolRegistry.class);

        when(toolCallRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(decisionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new HitlApprovalService(toolCallRepo, runRepo, decisionRepo, toolRegistry);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AgentToolCall pendingStep(Long id, Long runId, String tool, String args) {
        AgentToolCall tc = new AgentToolCall();
        tc.setRunId(runId); tc.setToolName(tool);
        tc.setHitlRequired(true); tc.setHitlOutcome("AWAITING_HITL");
        tc.setArgs(args); tc.setCalledAt(LocalDateTime.now());
        try { var f = AgentToolCall.class.getDeclaredField("id"); f.setAccessible(true); f.set(tc, id); }
        catch (Exception ignored) {}
        return tc;
    }

    private AgentRun run(Long id, Long agentId) {
        AgentRun r = new AgentRun();
        r.setAgentId(agentId); r.setProjectId(1L); r.setTriggeredBy("CRON");
        r.setStatus("COMPLETED"); r.setStartedAt(LocalDateTime.now());
        try { var f = AgentRun.class.getDeclaredField("id"); f.setAccessible(true); f.set(r, id); }
        catch (Exception ignored) {}
        return r;
    }

    private AgentTool tool(String id, Map<String, Object> result) {
        AgentTool t = mock(AgentTool.class);
        when(t.execute(any(), any())).thenReturn(result);
        return t;
    }

    // ── approve() ─────────────────────────────────────────────────────────────

    @Test
    void approveExecutesToolAndReturnsResult() {
        AgentToolCall tc = pendingStep(10L, 5L, "slack.send_channel", "{\"channel\":\"#orbit\"}");
        AgentRun r = run(5L, 2L);
        AgentTool t = tool("slack.send_channel", Map.of("ok", true, "ts", "178.001"));

        when(toolCallRepo.findById(10L)).thenReturn(Optional.of(tc));
        when(runRepo.findById(5L)).thenReturn(Optional.of(r));
        when(toolRegistry.find("slack.send_channel")).thenReturn(Optional.of(t));

        Map<String, Object> result = service.approve(5L, 10L, null, "admin@orbit.io");

        assertTrue((Boolean) result.get("ok"));
        assertEquals("178.001", result.get("ts"));
        verify(t).execute(any(), any());
    }

    @Test
    void approveSetsHitlOutcomeToApproved() {
        AgentToolCall tc = pendingStep(10L, 5L, "email.send", null);
        AgentRun r = run(5L, 2L);
        AgentTool t = tool("email.send", Map.of("ok", true));
        when(toolCallRepo.findById(10L)).thenReturn(Optional.of(tc));
        when(runRepo.findById(5L)).thenReturn(Optional.of(r));
        when(toolRegistry.find("email.send")).thenReturn(Optional.of(t));

        service.approve(5L, 10L, null, "admin@orbit.io");

        ArgumentCaptor<AgentToolCall> captor = ArgumentCaptor.forClass(AgentToolCall.class);
        verify(toolCallRepo).save(captor.capture());
        assertEquals("APPROVED", captor.getValue().getHitlOutcome());
    }

    @Test
    void approveStoresResultAsJson() throws Exception {
        AgentToolCall tc = pendingStep(10L, 5L, "slack.send_channel", null);
        AgentRun r = run(5L, 2L);
        AgentTool t = tool("slack.send_channel", Map.of("ok", true, "channel", "#orbit"));
        when(toolCallRepo.findById(10L)).thenReturn(Optional.of(tc));
        when(runRepo.findById(5L)).thenReturn(Optional.of(r));
        when(toolRegistry.find("slack.send_channel")).thenReturn(Optional.of(t));

        service.approve(5L, 10L, null, "admin@orbit.io");

        ArgumentCaptor<AgentToolCall> captor = ArgumentCaptor.forClass(AgentToolCall.class);
        verify(toolCallRepo).save(captor.capture());
        var node = om.readTree(captor.getValue().getResult());
        assertTrue(node.get("ok").asBoolean());
        assertEquals("#orbit", node.get("channel").asText());
    }

    @Test
    void approveWithEditedArgsPassesThemToTool() {
        AgentToolCall tc = pendingStep(10L, 5L, "email.send", "{\"to\":\"old@example.com\"}");
        AgentRun r = run(5L, 2L);
        Map<String, Object> editedArgs = Map.of("to", "new@example.com", "subject", "Updated");
        AgentTool t = tool("email.send", Map.of("ok", true));
        when(toolCallRepo.findById(10L)).thenReturn(Optional.of(tc));
        when(runRepo.findById(5L)).thenReturn(Optional.of(r));
        when(toolRegistry.find("email.send")).thenReturn(Optional.of(t));

        service.approve(5L, 10L, editedArgs, "admin@orbit.io");

        verify(t).execute(eq(editedArgs), any(AgentRunContext.class));
    }

    @Test
    void approveWritesDecisionLog() {
        AgentToolCall tc = pendingStep(10L, 5L, "slack.send_channel", null);
        AgentRun r = run(5L, 2L);
        AgentTool t = tool("slack.send_channel", Map.of("ok", true));
        when(toolCallRepo.findById(10L)).thenReturn(Optional.of(tc));
        when(runRepo.findById(5L)).thenReturn(Optional.of(r));
        when(toolRegistry.find("slack.send_channel")).thenReturn(Optional.of(t));

        service.approve(5L, 10L, null, "priya@example.com");

        ArgumentCaptor<AgentDecisionLog> captor = ArgumentCaptor.forClass(AgentDecisionLog.class);
        verify(decisionRepo).save(captor.capture());
        assertEquals("APPROVED", captor.getValue().getOutcome());
        assertEquals("priya@example.com", captor.getValue().getDecidedBy());
    }

    @Test
    void approveThrowsWhenStepNotPending() {
        AgentToolCall tc = pendingStep(10L, 5L, "email.send", null);
        tc.setHitlOutcome("APPROVED");
        when(toolCallRepo.findById(10L)).thenReturn(Optional.of(tc));

        assertThrows(IllegalStateException.class, () ->
            service.approve(5L, 10L, null, "admin@orbit.io"));
    }

    @Test
    void approveThrowsWhenRunIdMismatch() {
        AgentToolCall tc = pendingStep(10L, 999L, "email.send", null);
        when(toolCallRepo.findById(10L)).thenReturn(Optional.of(tc));

        assertThrows(IllegalArgumentException.class, () ->
            service.approve(5L, 10L, null, "admin@orbit.io"));
    }

    @Test
    void approveThrowsWhenStepNotFound() {
        when(toolCallRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
            service.approve(5L, 999L, null, "admin@orbit.io"));
    }

    @Test
    void approveHandlesToolExecutionFailureGracefully() {
        AgentToolCall tc = pendingStep(10L, 5L, "email.send", null);
        AgentRun r = run(5L, 2L);
        AgentTool t = mock(AgentTool.class);
        when(t.execute(any(), any())).thenThrow(new RuntimeException("SMTP connection refused"));
        when(toolCallRepo.findById(10L)).thenReturn(Optional.of(tc));
        when(runRepo.findById(5L)).thenReturn(Optional.of(r));
        when(toolRegistry.find("email.send")).thenReturn(Optional.of(t));

        Map<String, Object> result = service.approve(5L, 10L, null, "admin@orbit.io");

        assertNotNull(result.get("error"));
        assertTrue(result.get("error").toString().contains("SMTP connection refused"));

        ArgumentCaptor<AgentToolCall> captor = ArgumentCaptor.forClass(AgentToolCall.class);
        verify(toolCallRepo).save(captor.capture());
        assertEquals("APPROVED_WITH_ERROR", captor.getValue().getHitlOutcome());
    }

    // ── reject() ──────────────────────────────────────────────────────────────

    @Test
    void rejectSetsHitlOutcomeToRejected() {
        AgentToolCall tc = pendingStep(10L, 5L, "email.send", null);
        AgentRun r = run(5L, 2L);
        when(toolCallRepo.findById(10L)).thenReturn(Optional.of(tc));
        when(runRepo.findById(5L)).thenReturn(Optional.of(r));

        service.reject(5L, 10L, "Wrong recipient", "admin@orbit.io");

        ArgumentCaptor<AgentToolCall> captor = ArgumentCaptor.forClass(AgentToolCall.class);
        verify(toolCallRepo).save(captor.capture());
        assertEquals("REJECTED", captor.getValue().getHitlOutcome());
        assertEquals("Wrong recipient", captor.getValue().getHitlNote());
    }

    @Test
    void rejectNeverCallsToolExecute() {
        AgentToolCall tc = pendingStep(10L, 5L, "email.send", null);
        AgentRun r = run(5L, 2L);
        when(toolCallRepo.findById(10L)).thenReturn(Optional.of(tc));
        when(runRepo.findById(5L)).thenReturn(Optional.of(r));

        service.reject(5L, 10L, "Not approved", "admin@orbit.io");

        verify(toolRegistry, never()).find(any());
    }

    @Test
    void rejectWritesDecisionLogWithReason() {
        AgentToolCall tc = pendingStep(10L, 5L, "email.send", null);
        AgentRun r = run(5L, 2L);
        when(toolCallRepo.findById(10L)).thenReturn(Optional.of(tc));
        when(runRepo.findById(5L)).thenReturn(Optional.of(r));

        service.reject(5L, 10L, "Wrong channel — needs update", "priya@example.com");

        ArgumentCaptor<AgentDecisionLog> captor = ArgumentCaptor.forClass(AgentDecisionLog.class);
        verify(decisionRepo).save(captor.capture());
        assertEquals("REJECTED", captor.getValue().getOutcome());
        assertEquals("Wrong channel — needs update", captor.getValue().getOutcomeNote());
        assertEquals("priya@example.com", captor.getValue().getDecidedBy());
    }

    @Test
    void rejectThrowsWhenReasonBlank() {
        AgentToolCall tc = pendingStep(10L, 5L, "email.send", null);
        when(toolCallRepo.findById(10L)).thenReturn(Optional.of(tc));

        assertThrows(IllegalArgumentException.class, () ->
            service.reject(5L, 10L, "", "admin@orbit.io"));
        assertThrows(IllegalArgumentException.class, () ->
            service.reject(5L, 10L, "   ", "admin@orbit.io"));
    }

    @Test
    void rejectThrowsWhenStepAlreadyActioned() {
        AgentToolCall tc = pendingStep(10L, 5L, "email.send", null);
        tc.setHitlOutcome("REJECTED");
        when(toolCallRepo.findById(10L)).thenReturn(Optional.of(tc));

        assertThrows(IllegalStateException.class, () ->
            service.reject(5L, 10L, "Duplicate rejection", "admin@orbit.io"));
    }

    @Test
    void rejectThrowsWhenStepNotFound() {
        when(toolCallRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
            service.reject(5L, 999L, "reason", "admin@orbit.io"));
    }
}
