package com.orbit.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.agent.AgentDecisionLog;
import com.orbit.domain.agent.AgentRun;
import com.orbit.domain.agent.AgentToolCall;
import com.orbit.repository.AgentDecisionLogRepository;
import com.orbit.repository.AgentRunRepository;
import com.orbit.repository.AgentToolCallRepository;
import com.orbit.service.agent.tool.AgentRunContext;
import com.orbit.service.agent.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class HitlApprovalService {

    private static final Logger log = LoggerFactory.getLogger(HitlApprovalService.class);

    private final AgentToolCallRepository toolCallRepo;
    private final AgentRunRepository runRepo;
    private final AgentDecisionLogRepository decisionRepo;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HitlApprovalService(AgentToolCallRepository toolCallRepo,
                                AgentRunRepository runRepo,
                                AgentDecisionLogRepository decisionRepo,
                                ToolRegistry toolRegistry) {
        this.toolCallRepo = toolCallRepo;
        this.runRepo = runRepo;
        this.decisionRepo = decisionRepo;
        this.toolRegistry = toolRegistry;
    }

    @Transactional
    public Map<String, Object> approve(Long runId, Long stepId,
                                       Map<String, Object> editedArgs,
                                       String decidedBy) {
        AgentToolCall tc = findPendingStep(runId, stepId);
        AgentRun run = findRun(runId);

        Map<String, Object> args = resolveArgs(tc, editedArgs);
        AgentRunContext ctx = new AgentRunContext(run.getAgentId(), run.getId(),
            run.getProjectId(), run.getTriggeredBy());

        Map<String, Object> result;
        String hitlOutcome;
        try {
            result = toolRegistry.find(tc.getToolName())
                .orElseThrow(() -> new IllegalStateException("Tool not found: " + tc.getToolName()))
                .execute(args, ctx);
            hitlOutcome = "APPROVED";
            tc.setResult(toJson(result));
        } catch (Exception e) {
            log.warn("HITL-approved tool {} failed: {}", tc.getToolName(), e.getMessage());
            result = Map.of("error", e.getMessage() != null ? e.getMessage() : "execution_failed");
            hitlOutcome = "APPROVED_WITH_ERROR";
            tc.setResult(toJson(result));
        }

        tc.setHitlOutcome(hitlOutcome);
        tc.setHitlNote("Approved by " + decidedBy);
        toolCallRepo.save(tc);

        writeDecisionLog(run, tc, hitlOutcome, null, decidedBy);
        return result;
    }

    @Transactional
    public void reject(Long runId, Long stepId, String reason, String decidedBy) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        AgentToolCall tc = findPendingStep(runId, stepId);
        AgentRun run = findRun(runId);

        tc.setHitlOutcome("REJECTED");
        tc.setHitlNote(reason);
        tc.setResult("{\"status\":\"rejected\",\"reason\":\"" + escapeJson(reason) + "\"}");
        toolCallRepo.save(tc);

        writeDecisionLog(run, tc, "REJECTED", reason, decidedBy);
    }

    private AgentToolCall findPendingStep(Long runId, Long stepId) {
        AgentToolCall tc = toolCallRepo.findById(stepId)
            .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepId));
        if (!runId.equals(tc.getRunId())) {
            throw new IllegalArgumentException("Step does not belong to run " + runId);
        }
        if (!"AWAITING_HITL".equals(tc.getHitlOutcome())) {
            throw new IllegalStateException("Step is not awaiting HITL — current state: " + tc.getHitlOutcome());
        }
        return tc;
    }

    private AgentRun findRun(Long runId) {
        return runRepo.findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
    }

    private Map<String, Object> resolveArgs(AgentToolCall tc, Map<String, Object> editedArgs) {
        if (editedArgs != null && !editedArgs.isEmpty()) return editedArgs;
        if (tc.getArgs() == null || tc.getArgs().isBlank()) return Map.of();
        try {
            return objectMapper.readValue(tc.getArgs(), new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void writeDecisionLog(AgentRun run, AgentToolCall tc, String outcome,
                                  String note, String decidedBy) {
        // Resolve agent name from run
        String agentName = "Agent #" + run.getAgentId();
        AgentDecisionLog log = AgentDecisionLog.builder()
            .agentName(agentName)
            .triggerEvent("HITL:" + tc.getToolName() + " in run #" + run.getId())
            .proposalJson(tc.getArgs())
            .outcome(outcome)
            .outcomeNote(note)
            .decidedBy(decidedBy)
            .decidedAt(LocalDateTime.now())
            .build();
        decisionRepo.save(log);
    }

    private String toJson(Object v) {
        try { return objectMapper.writeValueAsString(v); } catch (Exception e) { return "{}"; }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
