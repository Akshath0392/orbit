package com.orbit.service.ai;

public interface AiGateway {
    String complete(String systemPrompt, String userMessage);

    default String complete(String systemPrompt, String userMessage, String model) {
        return complete(systemPrompt, userMessage);
    }
}
