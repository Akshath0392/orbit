package com.orbit.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.agent.AgentDefinition;
import com.orbit.domain.agent.AgentRun;
import com.orbit.domain.agent.AgentToolCall;
import com.orbit.repository.AgentRunRepository;
import com.orbit.repository.AgentToolCallRepository;
import com.orbit.service.agent.AgentRuntime;
import com.orbit.service.agent.tool.AgentRunContext;
import com.orbit.service.agent.tool.AgentTool;
import com.orbit.service.agent.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentRuntimeTest {

    AgentRunRepository      runRepo;
    AgentToolCallRepository callRepo;
    ToolRegistry            registry;
    AgentRuntime            runtime;
    org.springframework.context.ApplicationEventPublisher events;
    ObjectMapper            objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        runRepo  = mock(AgentRunRepository.class);
        callRepo = mock(AgentToolCallRepository.class);
        events   = mock(org.springframework.context.ApplicationEventPublisher.class);

        when(runRepo.save(any(AgentRun.class))).thenAnswer(inv -> {
            AgentRun r = inv.getArgument(0);
            if (r.getId() == null) {
                try { var f = AgentRun.class.getDeclaredField("id"); f.setAccessible(true); f.set(r, 1L); }
                catch (Exception ignored) {}
            }
            return r;
        });
        when(callRepo.save(any(AgentToolCall.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ToolRegistry registryWith(AgentTool... tools) {
        return new ToolRegistry(List.of(tools));
    }

    private AgentTool tool(String id, boolean hitl) {
        return new AgentTool() {
            public String id()            { return id; }
            public String description()   { return id; }
            public boolean requiresHitl() { return hitl; }
            public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
                return Map.of("result", "ok", "tool", id);
            }
        };
    }

    private AgentDefinition def(String... toolIds) {
        AgentDefinition d = new AgentDefinition();
        d.setName("TestAgent"); d.setAgentType("INTELLIGENCE"); d.setTriggerType("MANUAL");
        d.setTools(toolIds); d.setEnabled(true);
        try { var f = AgentDefinition.class.getDeclaredField("id"); f.setAccessible(true); f.set(d, 99L); }
        catch (Exception ignored) {}
        return d;
    }

    // ── Run persistence ───────────────────────────────────────────────────────

    @Test
    void executePersistsRunRecord() {
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(tool("memory.read", false)), events);
        runtime.execute(def("memory.read"), null, "MANUAL", Map.of());
        verify(runRepo, atLeast(2)).save(any(AgentRun.class));
    }

    @Test
    void executeCompletesSuccessfullyForNonHitlTool() {
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(tool("memory.read", false)), events);
        AgentRun run = runtime.execute(def("memory.read"), null, "MANUAL", Map.of());
        assertEquals("COMPLETED", run.getStatus());
        assertNull(run.getErrorMessage());
    }

    @Test
    void executeStoresDurationMs() {
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(tool("memory.read", false)), events);
        AgentRun run = runtime.execute(def("memory.read"), null, "MANUAL", Map.of());
        assertNotNull(run.getDurationMs());
        assertTrue(run.getDurationMs() >= 0);
    }

    @Test
    void executePassesProjectIdToContext() {
        Long[] captured = {null};
        AgentTool capturingTool = new AgentTool() {
            public String id()            { return "memory.read"; }
            public String description()   { return ""; }
            public boolean requiresHitl() { return false; }
            public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
                captured[0] = ctx.getProjectId();
                return Map.of();
            }
        };
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(capturingTool), events);
        runtime.execute(def("memory.read"), 42L, "MANUAL", Map.of());
        assertEquals(42L, captured[0]);
    }

    // ── HITL handling ─────────────────────────────────────────────────────────

    @Test
    void executeQueuesHitlToolWithoutCallingExecute() {
        AgentTool hitlTool = spy(tool("email.send", true));
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(hitlTool), events);
        runtime.execute(def("email.send"), null, "MANUAL", Map.of());

        verify(hitlTool, never()).execute(any(), any());

        ArgumentCaptor<AgentToolCall> captor = ArgumentCaptor.forClass(AgentToolCall.class);
        verify(callRepo).save(captor.capture());
        assertEquals("AWAITING_HITL", captor.getValue().getHitlOutcome());
        assertTrue(captor.getValue().getHitlRequired());
    }

    @Test
    void executeRunsNonHitlToolImmediately() {
        AgentTool nonHitlTool = spy(tool("memory.read", false));
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(nonHitlTool), events);
        runtime.execute(def("memory.read"), null, "MANUAL", Map.of());

        verify(nonHitlTool).execute(any(), any());

        ArgumentCaptor<AgentToolCall> captor = ArgumentCaptor.forClass(AgentToolCall.class);
        verify(callRepo).save(captor.capture());
        assertFalse(captor.getValue().getHitlRequired());
        assertNull(captor.getValue().getHitlOutcome());
    }

    @Test
    void slackSendChannelToolRequiresHitl() {
        // Security (audit M2): a channel broadcast is outbound + public and can carry
        // injectable content, so it must route through HITL approval like the DM/email tools.
        var slackTool = new com.orbit.service.agent.tool.SlackSendChannelTool(
            mock(com.orbit.integration.slack.SlackService.class));
        assertTrue(slackTool.requiresHitl(),
            "slack.send_channel must require HITL approval before broadcasting");
    }

    // ── JSON serialisation ────────────────────────────────────────────────────

    @Test
    void executeStoresInputContextAsValidJson() throws Exception {
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(tool("memory.read", false)), events);
        runtime.execute(def("memory.read"), null, "MANUAL", Map.of("dryRun", true, "project", "orbit"));

        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(runRepo, atLeast(1)).save(captor.capture());

        AgentRun persisted = captor.getAllValues().stream()
            .filter(r -> r.getInputContext() != null).findFirst().orElseThrow();

        // Must be parseable JSON, not Java Map.toString() like {dryRun=true}
        assertDoesNotThrow(() -> objectMapper.readTree(persisted.getInputContext()),
            "inputContext must be valid JSON, not Map.toString()");

        var node = objectMapper.readTree(persisted.getInputContext());
        assertTrue(node.has("dryRun"), "inputContext JSON should contain dryRun key");
        assertTrue(node.get("dryRun").asBoolean());
    }

    @Test
    void executeStoresNullInputContextAsBraces() {
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(tool("memory.read", false)), events);
        runtime.execute(def("memory.read"), null, "MANUAL", null);

        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(runRepo, atLeast(1)).save(captor.capture());

        captor.getAllValues().stream()
            .filter(r -> r.getInputContext() != null)
            .forEach(r -> assertEquals("{}", r.getInputContext()));
    }

    @Test
    void executeStoresToolResultAsValidJson() throws Exception {
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(tool("orbit.get_cr_summary", false)), events);
        runtime.execute(def("orbit.get_cr_summary"), null, "MANUAL", Map.of());

        ArgumentCaptor<AgentToolCall> captor = ArgumentCaptor.forClass(AgentToolCall.class);
        verify(callRepo).save(captor.capture());

        String result = captor.getValue().getResult();
        assertNotNull(result);
        assertDoesNotThrow(() -> objectMapper.readTree(result),
            "tool call result must be valid JSON, not Map.toString()");

        var node = objectMapper.readTree(result);
        assertTrue(node.has("result"), "result JSON should contain 'result' key");
    }

    @Test
    void executeStoresHitlToolResultAsJsonWithAwaitingStatus() throws Exception {
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(tool("email.send", true)), events);
        runtime.execute(def("email.send"), null, "MANUAL", Map.of());

        ArgumentCaptor<AgentToolCall> captor = ArgumentCaptor.forClass(AgentToolCall.class);
        verify(callRepo).save(captor.capture());

        String result = captor.getValue().getResult();
        var node = objectMapper.readTree(result);
        assertEquals("awaiting_hitl", node.path("status").asText());
    }

    // ── Multiple tools ────────────────────────────────────────────────────────

    @Test
    void executeRecordsToolCallForEveryTool() {
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(
            tool("memory.read",  false),
            tool("memory.write", false)
        ), events);
        runtime.execute(def("memory.read", "memory.write"), null, "CRON", Map.of());
        verify(callRepo, times(2)).save(any(AgentToolCall.class));
    }

    @Test
    void executeMixedHitlAndNonHitlRunsOnlyNonHitl() {
        AgentTool nonHitl = spy(tool("memory.read", false));
        AgentTool hitl    = spy(tool("email.send",  true));
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(nonHitl, hitl), events);
        runtime.execute(def("memory.read", "email.send"), null, "MANUAL", Map.of());

        verify(nonHitl).execute(any(), any());
        verify(hitl, never()).execute(any(), any());
        verify(callRepo, times(2)).save(any(AgentToolCall.class));
    }

    @Test
    void executeSetsOutputSummaryWithToolNames() {
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(
            tool("orbit.get_cr_summary", false),
            tool("slack.send_channel",   false)
        ), events);
        AgentRun run = runtime.execute(
            def("orbit.get_cr_summary", "slack.send_channel"), null, "MANUAL", Map.of());

        assertNotNull(run.getOutputSummary());
        assertTrue(run.getOutputSummary().contains("orbit.get_cr_summary"));
        assertTrue(run.getOutputSummary().contains("slack.send_channel"));
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void executeHandlesUnknownToolGracefully() {
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(), events);
        AgentRun run = runtime.execute(def("nonexistent.tool"), null, "MANUAL", Map.of());
        assertEquals("COMPLETED", run.getStatus());
        verify(callRepo, never()).save(any());
    }

    @Test
    void executeHandlesNullToolListGracefully() {
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(), events);
        AgentDefinition d = def();
        d.setTools(null);
        AgentRun run = runtime.execute(d, null, "MANUAL", Map.of());
        assertEquals("COMPLETED", run.getStatus());
    }

    @Test
    void executeRecordsToolErrorInResultJsonWhenToolThrows() throws Exception {
        List<AgentToolCall> savedCalls = new ArrayList<>();
        AgentToolCallRepository capturingCallRepo = mock(AgentToolCallRepository.class);
        when(capturingCallRepo.save(any())).thenAnswer(inv -> {
            AgentToolCall tc = inv.getArgument(0);
            savedCalls.add(tc);
            return tc;
        });

        AgentTool throwingTool = new AgentTool() {
            public String id()            { return "orbit.create_alert"; }
            public String description()   { return ""; }
            public boolean requiresHitl() { return false; }
            public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
                throw new RuntimeException("DB connection failed");
            }
        };
        runtime = new AgentRuntime(runRepo, capturingCallRepo, registryWith(throwingTool), events);
        AgentRun run = runtime.execute(def("orbit.create_alert"), null, "MANUAL", Map.of());

        assertEquals("COMPLETED", run.getStatus());
        assertFalse(savedCalls.isEmpty());

        String result = savedCalls.get(0).getResult();
        var node = objectMapper.readTree(result);
        assertTrue(node.has("error"), "Tool error must be recorded in result JSON");
        assertTrue(node.get("error").asText().contains("DB connection failed"));
    }

    // ── AgentRunStepEvent emission (live-log wiring) ─────────────────────────

    @Test
    void emits_started_then_completed_step_events_for_non_hitl_tool() {
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(tool("memory.read", false)), events);
        runtime.execute(def("memory.read"), null, "MANUAL", Map.of());

        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeast(2)).publishEvent(cap.capture());
        var statuses = cap.getAllValues().stream()
            .filter(e -> e instanceof com.orbit.service.agent.event.AgentRunStepEvent)
            .map(e -> ((com.orbit.service.agent.event.AgentRunStepEvent) e).status())
            .toList();
        assertTrue(statuses.contains("STARTED"));
        assertTrue(statuses.contains("COMPLETED"));
    }

    @Test
    void emits_failed_step_event_when_tool_throws() {
        AgentTool throwingTool = new AgentTool() {
            public String id()            { return "orbit.create_alert"; }
            public String description()   { return ""; }
            public boolean requiresHitl() { return false; }
            public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
                throw new RuntimeException("boom");
            }
        };
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(throwingTool), events);
        runtime.execute(def("orbit.create_alert"), null, "MANUAL", Map.of());

        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeastOnce()).publishEvent(cap.capture());
        var failed = cap.getAllValues().stream()
            .filter(e -> e instanceof com.orbit.service.agent.event.AgentRunStepEvent)
            .map(e -> (com.orbit.service.agent.event.AgentRunStepEvent) e)
            .filter(e -> "FAILED".equals(e.status()))
            .toList();
        assertEquals(1, failed.size());
        assertTrue(failed.get(0).message().contains("boom"));
    }

    @Test
    void hitl_tool_emits_hitl_awaiting_event_but_not_a_step_event() {
        runtime = new AgentRuntime(runRepo, callRepo, registryWith(tool("email.send", true)), events);
        runtime.execute(def("email.send"), null, "MANUAL", Map.of());

        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeastOnce()).publishEvent(cap.capture());
        boolean hasStepEvent = cap.getAllValues().stream()
            .anyMatch(e -> e instanceof com.orbit.service.agent.event.AgentRunStepEvent);
        boolean hasHitlEvent = cap.getAllValues().stream()
            .anyMatch(e -> e instanceof com.orbit.service.agent.event.HitlAwaitingEvent);
        assertTrue(hasHitlEvent, "HitlAwaitingEvent must be published for HITL tools");
        assertFalse(hasStepEvent, "AgentRunStepEvent is not emitted for HITL tools — bridge republishes from HITL event");
    }
}
