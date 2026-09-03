package com.orbit.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.agent.AgentDefinition;
import com.orbit.domain.agent.AgentRun;
import com.orbit.domain.agent.AgentToolCall;
import com.orbit.repository.AgentRunRepository;
import com.orbit.repository.AgentToolCallRepository;
import com.orbit.service.agent.event.AgentRunStepEvent;
import com.orbit.service.agent.event.HitlAwaitingEvent;
import com.orbit.service.agent.tool.AgentRunContext;
import com.orbit.service.agent.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    private final AgentRunRepository runs;
    private final AgentToolCallRepository toolCalls;
    private final ToolRegistry tools;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentRuntime(AgentRunRepository runs,
                        AgentToolCallRepository toolCalls,
                        ToolRegistry tools,
                        ApplicationEventPublisher events) {
        this.runs = runs;
        this.toolCalls = toolCalls;
        this.tools = tools;
        this.events = events;
    }

    /**
     * Execute an agent definition run.
     * Non-HITL tools are executed immediately and their results recorded.
     * HITL tools are queued as AWAITING_HITL tool call records without being executed.
     *
     * @param def          the agent definition to run
     * @param projectId    optional project scope
     * @param triggeredBy  who or what triggered this run (e.g. "CRON", "WEBHOOK", "MANUAL")
     * @param inputContext arbitrary key/value context passed to the run
     * @return the persisted AgentRun record
     */
    public AgentRun execute(AgentDefinition def,
                            Long projectId,
                            String triggeredBy,
                            Map<String, Object> inputContext) {
        return execute(def, projectId, triggeredBy, inputContext, "SCHEDULED");
    }

    public AgentRun execute(AgentDefinition def,
                            Long projectId,
                            String triggeredBy,
                            Map<String, Object> inputContext,
                            String invocationSource) {
        AgentRun run = new AgentRun();
        run.setAgentId(def.getId());
        run.setProjectId(projectId);
        run.setTriggeredBy(triggeredBy);
        run.setStatus("RUNNING");
        run.setInvocationSource(invocationSource == null ? "SCHEDULED" : invocationSource);
        run.setStartedAt(LocalDateTime.now());
        try {
            run.setInputContext(inputContext != null ? objectMapper.writeValueAsString(inputContext) : "{}");
        } catch (Exception e) {
            run.setInputContext("{}");
        }
        runs.save(run);

        AgentRunContext ctx = new AgentRunContext(def.getId(), run.getId(), projectId, triggeredBy);
        long t0 = System.currentTimeMillis();

        try {
            StringBuilder summary = new StringBuilder();
            String[] toolIds = def.getTools();
            if (toolIds != null) {
                for (String toolId : toolIds) {
                    tools.find(toolId).ifPresent(tool -> {
                        AgentToolCall call = new AgentToolCall();
                        call.setRunId(run.getId());
                        call.setToolName(toolId);
                        call.setHitlRequired(tool.requiresHitl());
                        call.setCalledAt(LocalDateTime.now());

                        if (tool.requiresHitl()) {
                            call.setHitlOutcome("AWAITING_HITL");
                            String argsJson = inputContext == null ? "{}" : toJson(inputContext);
                            call.setArgs(argsJson);
                            call.setResult("{\"status\":\"awaiting_hitl\"}");
                            summary.append(toolId).append(":AWAITING_HITL; ");
                            toolCalls.save(call);
                            events.publishEvent(new HitlAwaitingEvent(
                                run.getId(), call.getId(), def.getName(),
                                toolId, argsJson, triggeredBy));
                            // HitlAwaitingEvent is republished as a step event by AgentRunStreamBridge —
                            // don't double-publish here.
                            return;
                        } else {
                            events.publishEvent(AgentRunStepEvent.started(run.getId(), null, toolId));
                            try {
                                Map<String, Object> result = tool.execute(inputContext != null ? inputContext : Map.of(), ctx);
                                call.setResult(toJson(result));
                                summary.append(toolId).append(":ok; ");
                                toolCalls.save(call);
                                events.publishEvent(AgentRunStepEvent.completed(
                                    run.getId(), call.getId(), toolId, "ok"));
                                return;
                            } catch (Exception e) {
                                call.setResult("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                                log.warn("Tool {} failed in run {}: {}", toolId, run.getId(), e.getMessage());
                                toolCalls.save(call);
                                events.publishEvent(AgentRunStepEvent.failed(
                                    run.getId(), call.getId(), toolId, e.getMessage()));
                                return;
                            }
                        }
                    });
                }
            }
            run.setStatus("COMPLETED");
            run.setOutputSummary(summary.toString());
        } catch (Exception e) {
            run.setStatus("FAILED");
            run.setErrorMessage(e.getMessage());
            log.error("Agent {} run {} failed: {}", def.getName(), run.getId(), e.getMessage());
        }

        run.setCompletedAt(LocalDateTime.now());
        run.setDurationMs((int) (System.currentTimeMillis() - t0));
        return runs.save(run);
    }

    private String toJson(Object v) {
        try { return objectMapper.writeValueAsString(v); } catch (Exception e) { return "{}"; }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
