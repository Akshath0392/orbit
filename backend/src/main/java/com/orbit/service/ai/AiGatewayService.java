package com.orbit.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@Primary
public class AiGatewayService implements AiGateway {

    // Honest, data-free notices. We NEVER fabricate delivery data (issue keys,
    // owners, counts) — a wrong-but-plausible answer is worse than no answer.
    static final String NOT_CONFIGURED =
        "Orbit AI isn't configured yet — no model API key is set. Add ANTHROPIC_API_KEY "
        + "(or set AI_PROVIDER=openai and OPENAI_API_KEY) and restart the backend. "
        + "I can't answer live questions until then.";
    static final String CALL_FAILED =
        "I couldn't reach the AI model just now — the request failed. Please try again in a moment.";

    @Value("${orbit.ai.provider:anthropic}")
    private String provider;

    @Value("${ANTHROPIC_API_KEY:}")
    private String anthropicKey;

    @Value("${OPENAI_API_KEY:}")
    private String openaiKey;

    @Value("${orbit.ai.model:claude-sonnet-4-6}")
    private String model;

    private final WebClient webClient = WebClient.create();

    @Override
    public String complete(String systemPrompt, String userMessage) {
        return complete(systemPrompt, userMessage, model);
    }

    @Override
    public String complete(String systemPrompt, String userMessage, String overrideModel) {
        String modelToUse = (overrideModel == null || overrideModel.isBlank()) ? model : overrideModel;
        boolean openai = "openai".equalsIgnoreCase(provider);
        String key = openai ? openaiKey : anthropicKey;
        if (!isConfigured(key)) return NOT_CONFIGURED;
        try {
            return openai
                ? callOpenAi(systemPrompt, userMessage, modelToUse)
                : callAnthropic(systemPrompt, userMessage, modelToUse);
        } catch (Exception e) {
            return CALL_FAILED;
        }
    }

    // A key counts as configured only when it's present AND not a placeholder.
    // The shipped .env carries `your_anthropic_api_key`; treating that as "set"
    // is what made every call 401 and fall through to fabricated text.
    static boolean isConfigured(String key) {
        if (key == null) return false;
        String k = key.trim();
        if (k.isBlank()) return false;
        String lower = k.toLowerCase();
        return !(lower.startsWith("your_") || lower.startsWith("your-")
            || lower.equals("changeme") || lower.equals("replace_me")
            || lower.equals("sk-xxx") || lower.contains("placeholder"));
    }

    private String callAnthropic(String systemPrompt, String userMessage, String modelToUse) {
        Map<String,Object> body = Map.of(
            "model", modelToUse,
            "max_tokens", 512,
            "system", systemPrompt,
            "messages", List.of(Map.of("role","user","content", userMessage))
        );
        @SuppressWarnings("rawtypes")
        Map resp = webClient.post()
            .uri("https://api.anthropic.com/v1/messages")
            .header("x-api-key", anthropicKey)
            .header("anthropic-version","2023-06-01")
            .header("content-type","application/json")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .block();
        if (resp != null && resp.get("content") instanceof List<?> content && !content.isEmpty()
            && content.get(0) instanceof Map<?,?> firstMap
            && firstMap.get("text") instanceof String s) {
            return s;
        }
        return CALL_FAILED;
    }

    private String callOpenAi(String systemPrompt, String userMessage, String modelToUse) {
        Map<String,Object> body = Map.of(
            "model", modelToUse,
            "max_tokens", 512,
            "messages", List.of(
                Map.of("role","system","content", systemPrompt),
                Map.of("role","user","content", userMessage))
        );
        @SuppressWarnings("rawtypes")
        Map resp = webClient.post()
            .uri("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer " + openaiKey)
            .header("content-type","application/json")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .block();
        if (resp != null && resp.get("choices") instanceof List<?> choices && !choices.isEmpty()
            && choices.get(0) instanceof Map<?,?> choice
            && choice.get("message") instanceof Map<?,?> msg
            && msg.get("content") instanceof String s) {
            return s;
        }
        return CALL_FAILED;
    }
}
