package com.orbit.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.agent.AgentDefinition;
import com.orbit.domain.agent.AgentRun;
import com.orbit.domain.client.AppUser;
import com.orbit.repository.AgentDefinitionRepository;
import com.orbit.repository.AgentRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Single entry point for invoking any Orbit agent.
 *
 * Resolves an agent key to either:
 *   1. a native @Service agent (via {@link NativeAgentRegistry}), or
 *   2. an {@link AgentDefinition} record executed by {@link AgentRuntime}.
 *
 * Records every invocation as an {@link AgentRun} carrying the originating
 * {@code invocationSource} (SCHEDULED, UI, SLACK_SLASH, SLACK_MENTION,
 * SLACK_BUTTON, WEBHOOK) so downstream audit/cost tooling can attribute
 * usage by surface.
 */
@Service
public class AgentInvocationService {

    private static final Logger log = LoggerFactory.getLogger(AgentInvocationService.class);

    private final NativeAgentRegistry nativeRegistry;
    private final AgentDefinitionRepository definitions;
    private final AgentRuntime runtime;
    private final AgentRunRepository runs;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentInvocationService(NativeAgentRegistry nativeRegistry,
                                  AgentDefinitionRepository definitions,
                                  AgentRuntime runtime,
                                  AgentRunRepository runs) {
        this.nativeRegistry = nativeRegistry;
        this.definitions = definitions;
        this.runtime = runtime;
        this.runs = runs;
    }

    public AgentInvocationResult invoke(AppUser user,
                                        String agentKey,
                                        Map<String, Object> args,
                                        String invocationSource) {
        return invoke(user, user != null ? user.getEmail() : null, agentKey, args, invocationSource);
    }

    public AgentInvocationResult invokeAs(String actorEmail,
                                          String agentKey,
                                          Map<String, Object> args,
                                          String invocationSource) {
        return invoke(null, actorEmail, agentKey, args, invocationSource);
    }

    /** Roles that can invoke any agent from Slack. Read-only Slack queries do not flow through here.
     *  Uses the live role set — V62 collapsed HEAD_PJM/PJM into PM, so the old "PJM" gate blocked
     *  every real PM from Slack invocation (audit M7). */
    private static final java.util.Set<String> SLACK_INVOKE_ROLES = java.util.Set.of("ADMIN", "PM");

    private AgentInvocationResult invoke(AppUser user,
                                         String actorEmail,
                                         String agentKey,
                                         Map<String, Object> args,
                                         String invocationSource) {
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        String source = invocationSource == null || invocationSource.isBlank() ? "MANUAL" : invocationSource;
        String actor = actorEmail != null && !actorEmail.isBlank() ? actorEmail : "system";

        if (source.startsWith("SLACK_")) {
            String role = user == null ? null : user.getRole();
            if (role == null || !SLACK_INVOKE_ROLES.contains(role.toUpperCase())) {
                log.warn("Slack invocation of '{}' blocked: role={} actor={}", agentKey, role, actor);
                return AgentInvocationResult.failed(null, agentKey,
                    "Your Orbit role (" + (role == null ? "unset" : role) + ") cannot run agents from Slack. "
                    + "Required: ADMIN or PM.");
            }
        }

        if (nativeRegistry.has(agentKey)) {
            return invokeNative(user, agentKey, safeArgs, source, actor);
        }

        Optional<AgentDefinition> def = definitions.findAll().stream()
            .filter(d -> agentKey.equalsIgnoreCase(d.getName()))
            .findFirst();
        if (def.isPresent()) {
            if (source.startsWith("SLACK_") && !Boolean.TRUE.equals(def.get().getSlackExposed())) {
                log.warn("Slack invocation of agent '{}' blocked: slack_exposed=false (actor={})", agentKey, actor);
                return AgentInvocationResult.failed(null, agentKey,
                    "Agent '" + agentKey + "' is not exposed to Slack. Enable it from the Agents admin page.");
            }
            AgentRun run = runtime.execute(def.get(), asLong(safeArgs.get("projectId")), actor, safeArgs, source);
            return run.getStatus().equals("FAILED")
                ? AgentInvocationResult.failed(run.getId(), agentKey, run.getErrorMessage())
                : AgentInvocationResult.completed(run.getId(), agentKey, run.getOutputSummary(), Map.of());
        }

        throw new IllegalArgumentException("Unknown agent key: " + agentKey);
    }

    private AgentInvocationResult invokeNative(AppUser user,
                                               String agentKey,
                                               Map<String, Object> args,
                                               String source,
                                               String actor) {
        AgentRun run = new AgentRun();
        run.setAgentId(null);
        run.setProjectId(asLong(args.get("projectId")));
        run.setTriggeredBy(actor);
        run.setStatus("RUNNING");
        run.setInvocationSource(source);
        run.setStartedAt(LocalDateTime.now());
        try {
            run.setInputContext(objectMapper.writeValueAsString(
                Map.of("agentKey", agentKey, "args", args)));
        } catch (Exception e) {
            run.setInputContext("{}");
        }
        runs.save(run);

        long t0 = System.currentTimeMillis();
        try {
            Map<String, Object> outputs = nativeRegistry.get(agentKey).apply(user, args);
            run.setStatus("COMPLETED");
            String summary = outputs == null || outputs.isEmpty() ? agentKey + ":ok" : agentKey + ":" + outputs;
            run.setOutputSummary(summary);
            run.setCompletedAt(LocalDateTime.now());
            run.setDurationMs((int) (System.currentTimeMillis() - t0));
            runs.save(run);
            return AgentInvocationResult.completed(run.getId(), agentKey, summary, outputs);
        } catch (Exception e) {
            run.setStatus("FAILED");
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(LocalDateTime.now());
            run.setDurationMs((int) (System.currentTimeMillis() - t0));
            runs.save(run);
            log.warn("Native agent {} failed for {}: {}", agentKey, actor, e.getMessage());
            return AgentInvocationResult.failed(run.getId(), agentKey, e.getMessage());
        }
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }
}
