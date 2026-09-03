package com.orbit.service.slack;

import com.orbit.service.ai.RecordedAiGateway;
import com.orbit.service.slack.IntentResolver.ResolvedIntent;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Replays the Slack-status-query eval set (docs/agent-evals/slack-status-queries.yaml)
 * against {@link IntentResolver}. Each YAML case becomes one JUnit dynamic test.
 *
 * For natural-language cases, {@code stubbedLlmJson} primes {@link RecordedAiGateway}
 * so we don't hit the real model in CI.
 *
 * Add new utterances to the YAML, not here — the runner adapts automatically.
 */
class SlackEvalRunnerTest {

    @TestFactory
    @SuppressWarnings("unchecked")
    Iterable<DynamicTest> slack_status_queries_match_expectations() throws IOException {
        List<Map<String, Object>> cases = loadCases();
        assertThat(cases).as("eval set must not shrink unintentionally").hasSizeGreaterThanOrEqualTo(28);

        List<DynamicTest> tests = new ArrayList<>(cases.size());
        for (Map<String, Object> c : cases) {
            String name = (String) c.get("name");
            String utterance = (String) c.get("utterance");
            Object expectedTool = c.get("expectedTool");
            Map<String, Object> expectedArgs = (Map<String, Object>) c.getOrDefault("expectedArgs", Map.of());
            String stub = (String) c.get("stubbedLlmJson");

            tests.add(dynamicTest(name + " — \"" + utterance + "\"", () -> {
                RecordedAiGateway ai = new RecordedAiGateway();
                if (stub != null) ai.defaultResponse(stub);
                IntentResolver resolver = new IntentResolver(ai);

                Optional<ResolvedIntent> got = resolver.resolve(utterance);

                if (expectedTool == null) {
                    assertThat(got)
                        .as("expected no match for utterance: %s", utterance)
                        .isEmpty();
                    return;
                }
                assertThat(got)
                    .as("expected match for utterance: %s", utterance)
                    .isPresent();
                assertThat(got.get().tool())
                    .as("tool for utterance: %s", utterance)
                    .isEqualTo(expectedTool);
                for (Map.Entry<String, Object> kv : expectedArgs.entrySet()) {
                    assertThat(got.get().args())
                        .as("arg %s for utterance: %s", kv.getKey(), utterance)
                        .containsEntry(kv.getKey(), kv.getValue());
                }
            }));
        }
        return tests;
    }

    private static List<Map<String, Object>> loadCases() throws IOException {
        // Prefer classpath (so the YAML file is in src/test/resources if relocated);
        // fall back to docs/ in the repo root, which is the canonical location.
        Path repoRoot = Path.of("").toAbsolutePath();
        Path docsPath = repoRoot.getParent() == null
            ? repoRoot.resolve("docs/agent-evals/slack-status-queries.yaml")
            : repoRoot.resolve("docs/agent-evals/slack-status-queries.yaml");
        if (!Files.exists(docsPath)) {
            // Running from repo root vs from backend/: try going up one level.
            docsPath = repoRoot.getParent().resolve("docs/agent-evals/slack-status-queries.yaml");
        }
        try (InputStream in = Files.newInputStream(docsPath)) {
            Yaml yaml = new Yaml();
            return yaml.load(in);
        }
    }
}
