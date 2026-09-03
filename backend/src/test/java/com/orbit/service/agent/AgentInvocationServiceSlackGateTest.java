package com.orbit.service.agent;

import com.orbit.domain.agent.AgentDefinition;
import com.orbit.domain.agent.AgentRun;
import com.orbit.repository.AgentDefinitionRepository;
import com.orbit.repository.AgentRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Phase 2.4: Slack-originated invocations of YAML-defined AgentDefinition rows must be
 * gated by {@code slack_exposed=true}. Native @Service agents are not gated (they're
 * built-in and curated).
 */
class AgentInvocationServiceSlackGateTest {

    NativeAgentRegistry nativeRegistry;
    AgentDefinitionRepository definitions;
    AgentRuntime runtime;
    AgentRunRepository runs;
    AgentInvocationService service;

    @BeforeEach
    void setUp() {
        nativeRegistry = mock(NativeAgentRegistry.class);
        definitions = mock(AgentDefinitionRepository.class);
        runtime = mock(AgentRuntime.class);
        runs = mock(AgentRunRepository.class);
        service = new AgentInvocationService(nativeRegistry, definitions, runtime, runs);
    }

    private static AgentDefinition def(String name, Boolean slackExposed) {
        AgentDefinition d = new AgentDefinition();
        d.setName(name);
        d.setSlackExposed(slackExposed);
        return d;
    }

    @Test
    void slack_invocation_of_definition_without_slack_exposed_is_blocked() {
        when(nativeRegistry.has("custom.report")).thenReturn(false);
        when(definitions.findAll()).thenReturn(List.of(def("custom.report", false)));

        com.orbit.domain.client.AppUser user = new com.orbit.domain.client.AppUser();
        user.setEmail("u@orbit.io"); user.setRole("PM");
        AgentInvocationResult r = service.invoke(user, "custom.report", Map.of(), "SLACK_SLASH");

        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.summary()).contains("not exposed to Slack");
        verifyNoInteractions(runtime);
    }

    @Test
    void slack_invocation_of_definition_with_slack_exposed_true_runs() {
        when(nativeRegistry.has("custom.report")).thenReturn(false);
        when(definitions.findAll()).thenReturn(List.of(def("custom.report", true)));
        AgentRun run = new AgentRun();
        org.springframework.test.util.ReflectionTestUtils.setField(run, "id", 11L);
        run.setStatus("COMPLETED"); run.setOutputSummary("ok");
        when(runtime.execute(any(), any(), eq("u@orbit.io"), any(), eq("SLACK_SLASH"))).thenReturn(run);

        com.orbit.domain.client.AppUser user = new com.orbit.domain.client.AppUser();
        user.setEmail("u@orbit.io"); user.setRole("PM");
        AgentInvocationResult r = service.invoke(user, "custom.report", Map.of(), "SLACK_SLASH");

        assertThat(r.status()).isEqualTo("COMPLETED");
        assertThat(r.runId()).isEqualTo(11L);
        verify(runtime).execute(any(), any(), eq("u@orbit.io"), any(), eq("SLACK_SLASH"));
    }

    @Test
    void slack_invocation_with_unprivileged_role_is_blocked_before_slack_exposed_check() {
        com.orbit.domain.client.AppUser user = new com.orbit.domain.client.AppUser();
        user.setEmail("dev@orbit.io"); user.setRole("DEVELOPER");

        AgentInvocationResult r = service.invoke(user, "custom.report", Map.of(), "SLACK_SLASH");

        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.summary()).contains("cannot run agents from Slack", "DEVELOPER");
        verifyNoInteractions(definitions, runtime);
    }

    @Test
    void slack_invocation_with_null_role_is_blocked() {
        com.orbit.domain.client.AppUser user = new com.orbit.domain.client.AppUser();
        user.setEmail("u@orbit.io");

        AgentInvocationResult r = service.invoke(user, "report.draft", Map.of(), "SLACK_SLASH");

        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.summary()).contains("unset");
        verifyNoInteractions(nativeRegistry, definitions, runtime);
    }

    @Test
    void non_slack_invocation_of_definition_runs_regardless_of_flag() {
        when(nativeRegistry.has("custom.report")).thenReturn(false);
        when(definitions.findAll()).thenReturn(List.of(def("custom.report", false)));
        AgentRun run = new AgentRun();
        org.springframework.test.util.ReflectionTestUtils.setField(run, "id", 22L);
        run.setStatus("COMPLETED"); run.setOutputSummary("ok");
        when(runtime.execute(any(), any(), eq("system"), any(), eq("SCHEDULED"))).thenReturn(run);

        AgentInvocationResult r = service.invokeAs(null, "custom.report", Map.of(), "SCHEDULED");

        assertThat(r.status()).isEqualTo("COMPLETED");
        verify(runtime).execute(any(), any(), eq("system"), any(), eq("SCHEDULED"));
    }
}
