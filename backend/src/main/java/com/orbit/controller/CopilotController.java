package com.orbit.controller;

import com.orbit.service.ai.AiGatewayService;
import com.orbit.service.ai.CopilotContextService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/copilot")
public class CopilotController {

    private final SimpMessagingTemplate ws;
    private final AiGatewayService ai;
    private final CopilotContextService context;

    public CopilotController(SimpMessagingTemplate ws, AiGatewayService ai, CopilotContextService context) {
        this.ws = ws; this.ai = ai; this.context = context;
    }

    @PostMapping("/message")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> message(@RequestBody Map<String,Object> body, Authentication auth) {
        String sessionId = (String) body.getOrDefault("sessionId", "default");
        String text      = (String) body.getOrDefault("text","");
        Long portfolioId = asLong(body.get("portfolioId"));
        String topic     = "/topic/copilot/" + sessionId;
        String systemPrompt = buildGroundedPrompt(portfolioId);

        CompletableFuture.runAsync(() -> {
            try {
                String response = ai.complete(systemPrompt, text);
                for (String word : response.split(" ")) {
                    ws.convertAndSend(topic, Map.of("type","token","content", word + " "));
                    Thread.sleep(40);
                }
                ws.convertAndSend(topic, Map.of("type","done"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ws.convertAndSend(topic, Map.of("type","done"));
            }
        });

        return ResponseEntity.accepted().build();
    }

    // System prompt grounded in the live delivery snapshot for the given portfolio.
    String buildGroundedPrompt(Long portfolioId) {
        return """
            You are Orbit Copilot, an AI delivery intelligence assistant for project managers.
            Help PJMs understand project risks, manage CRs, and take action on alerts.
            Be concise, actionable, and specific. Reference issue keys when relevant.
            Answer from the CURRENT DELIVERY CONTEXT below. If it doesn't cover the question,
            say so plainly rather than guessing — never invent issue keys, names, or numbers.

            CURRENT DELIVERY CONTEXT (live):
            """ + context.buildDigest(portfolioId);
    }

    private Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
