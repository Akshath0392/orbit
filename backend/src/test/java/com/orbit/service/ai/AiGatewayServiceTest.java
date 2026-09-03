package com.orbit.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway must never fabricate delivery data. A placeholder or blank API
 * key is "unconfigured" — it returns an honest notice, not a made-up answer.
 */
class AiGatewayServiceTest {

    private AiGatewayService gateway(String provider, String anthropicKey, String openaiKey) {
        AiGatewayService ai = new AiGatewayService();
        ReflectionTestUtils.setField(ai, "provider", provider);
        ReflectionTestUtils.setField(ai, "anthropicKey", anthropicKey);
        ReflectionTestUtils.setField(ai, "openaiKey", openaiKey);
        ReflectionTestUtils.setField(ai, "model", "claude-sonnet-4-6");
        return ai;
    }

    @Test
    void placeholderAnthropicKeyReturnsHonestNoticeNotFabricatedData() {
        AiGatewayService ai = gateway("anthropic", "your_anthropic_api_key", "");
        String out = ai.complete("sys", "what is at risk today?");
        assertThat(out).isEqualTo(AiGatewayService.NOT_CONFIGURED);
        // The old fabricated fallback must be gone.
        assertThat(out).doesNotContain("NX-884").doesNotContain("Sigma").doesNotContain("Rajan");
    }

    @Test
    void blankAnthropicKeyIsUnconfigured() {
        AiGatewayService ai = gateway("anthropic", "", "");
        assertThat(ai.complete("sys", "hi")).isEqualTo(AiGatewayService.NOT_CONFIGURED);
    }

    @Test
    void openaiProviderChecksTheOpenaiKeyNotAnthropic() {
        // Real-looking Anthropic key but provider=openai with a placeholder OpenAI key
        // → still unconfigured, because the active provider's key is what matters.
        AiGatewayService ai = gateway("openai", "sk-ant-realish-key", "your_openai_api_key");
        assertThat(ai.complete("sys", "hi")).isEqualTo(AiGatewayService.NOT_CONFIGURED);
    }

    @Test
    void isConfiguredBoundaries() {
        assertThat(AiGatewayService.isConfigured(null)).isFalse();
        assertThat(AiGatewayService.isConfigured("   ")).isFalse();
        assertThat(AiGatewayService.isConfigured("your_anthropic_api_key")).isFalse();
        assertThat(AiGatewayService.isConfigured("YOUR-KEY-HERE")).isFalse();
        assertThat(AiGatewayService.isConfigured("changeme")).isFalse();
        assertThat(AiGatewayService.isConfigured("sk-xxx")).isFalse();
        assertThat(AiGatewayService.isConfigured("placeholder-token")).isFalse();
        assertThat(AiGatewayService.isConfigured("sk-ant-api03-abcdef123456")).isTrue();
        assertThat(AiGatewayService.isConfigured("sk-proj-abcdef123456")).isTrue();
    }
}
