package com.orbit.controller;

import com.orbit.service.ai.AiGatewayService;
import com.orbit.service.ai.CopilotContextService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CopilotControllerTest {

    @Test
    void groundedPromptEmbedsTheLiveDigestForThePortfolio() {
        CopilotContextService context = mock(CopilotContextService.class);
        when(context.buildDigest(5L)).thenReturn("OPEN ALERTS: 2 critical, 1 risk\nOPEN CRs: 7");

        CopilotController controller = new CopilotController(
            mock(SimpMessagingTemplate.class), new AiGatewayService(), context);

        String prompt = controller.buildGroundedPrompt(5L);

        assertThat(prompt).contains("You are Orbit Copilot");
        assertThat(prompt).contains("CURRENT DELIVERY CONTEXT (live):");
        assertThat(prompt).contains("OPEN ALERTS: 2 critical, 1 risk");
        assertThat(prompt).contains("OPEN CRs: 7");
        // Guardrail instruction against fabrication is present.
        assertThat(prompt).contains("never invent issue keys");
    }
}
