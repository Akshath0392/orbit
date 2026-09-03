package com.orbit.integration.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.domain.issue.JiraWebhookEvent;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.JiraWebhookEventRepository;
import com.orbit.service.agent.DeliveryIntelligenceAgent;
import com.orbit.service.sync.BertTriageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receives inbound Jira webhook events.
 *
 * <p>Security: validates {@code X-Hub-Signature-256} HMAC before processing.
 * <p>Idempotency: deduplicates on {@code webhookEvent} + issue key (used as webhookId).
 * <p>Processing: upserts the {@link JiraIssue}, then fires async
 * {@link BertTriageService#classifyIssue} and {@link DeliveryIntelligenceAgent#onIssueEvent}.
 */
@RestController
@RequestMapping("/api/jira")
public class JiraWebhookController {

    private static final Logger log = LoggerFactory.getLogger(JiraWebhookController.class);

    @Value("${orbit.jira.webhook-secret:dev-secret}")
    private String webhookSecretFallback;

    private final org.springframework.core.env.Environment environment;

    private final JiraIssueRepository jiraIssueRepository;
    private final JiraWebhookEventRepository webhookEventRepository;
    private final BertTriageService bertTriageService;
    private final DeliveryIntelligenceAgent deliveryAgent;
    private final ObjectMapper objectMapper;
    private final com.orbit.repository.JiraConfigRepository jiraConfigRepository;
    private final com.orbit.service.sync.IssueTransitionService issueTransitions;
    private final com.orbit.service.sync.SprintIngestService sprintIngest;

    public JiraWebhookController(JiraIssueRepository jiraIssueRepository,
                                  JiraWebhookEventRepository webhookEventRepository,
                                  BertTriageService bertTriageService,
                                  DeliveryIntelligenceAgent deliveryAgent,
                                  ObjectMapper objectMapper,
                                  com.orbit.repository.JiraConfigRepository jiraConfigRepository,
                                  com.orbit.service.sync.IssueTransitionService issueTransitions,
                                  com.orbit.service.sync.SprintIngestService sprintIngest,
                                  org.springframework.core.env.Environment environment) {
        this.jiraIssueRepository = jiraIssueRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.bertTriageService = bertTriageService;
        this.deliveryAgent = deliveryAgent;
        this.objectMapper = objectMapper;
        this.jiraConfigRepository = jiraConfigRepository;
        this.issueTransitions = issueTransitions;
        this.sprintIngest = sprintIngest;
        this.environment = environment;
    }

    @PostConstruct
    void validateWebhookSecret() {
        if ("dev-secret".equals(webhookSecretFallback)) {
            log.warn("SECURITY: orbit.jira.webhook-secret is set to the default 'dev-secret'. "
                + "Set ORBIT_JIRA_WEBHOOK_SECRET env var before receiving live Jira webhooks.");
        }
    }

    /**
     * POST /api/jira/webhook
     *
     * <p>Jira sends a JSON body with fields:
     * <ul>
     *   <li>{@code webhookEvent} — e.g. "jira:issue_updated"</li>
     *   <li>{@code issue.key}</li>
     *   <li>{@code issue.fields.summary}</li>
     *   <li>{@code issue.fields.status.name}</li>
     * </ul>
     *
     * <p>Returns 200 immediately after validation; all processing is async.
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-Atlassian-Webhook-Identifier", required = false) String deliveryId,
            @RequestBody String rawBody) {

        // 1. Validate HMAC signature
        if (!isSignatureValid(signature, rawBody)) {
            log.warn("JiraWebhookController: Invalid or missing signature");
            LinkedHashMap<String, Object> err = new LinkedHashMap<>();
            err.put("error", "Invalid signature");
            return ResponseEntity.status(401).body(err);
        }

        // 2. Parse JSON body
        Map<String, Object> payload;
        try {
            //noinspection unchecked
            payload = objectMapper.readValue(rawBody, Map.class);
        } catch (Exception e) {
            log.error("JiraWebhookController: Failed to parse JSON body — {}", e.getMessage());
            LinkedHashMap<String, Object> err = new LinkedHashMap<>();
            err.put("error", "Invalid JSON");
            return ResponseEntity.badRequest().body(err);
        }

        String webhookEvent = (String) payload.getOrDefault("webhookEvent", "unknown");
        String issueKey = extractIssueKey(payload);
        String summary = extractSummary(payload);
        String statusName = extractStatusName(payload);

        // 3. Idempotency check (L3) — dedupe on a STABLE id so a replayed signed
        // payload is a no-op regardless of elapsed time. Prefer Jira's delivery id
        // header, then the body's own timestamp; only fall back to a per-minute
        // wall-clock bucket when neither is present.
        String stableId;
        if (deliveryId != null && !deliveryId.isBlank()) {
            stableId = "d:" + deliveryId.trim();
        } else if (payload.get("timestamp") != null) {
            stableId = "t:" + payload.get("timestamp");
        } else {
            stableId = "m:" + (System.currentTimeMillis() / 60000);
        }
        String webhookId = webhookEvent + ":" + issueKey + ":" + stableId;
        if (webhookEventRepository.findByWebhookId(webhookId).isPresent()) {
            log.info("JiraWebhookController: Duplicate webhook id={} — skipping", webhookId);
            return ResponseEntity.ok().build();
        }

        // Persist the webhook event record
        JiraWebhookEvent record = JiraWebhookEvent.builder()
                .webhookId(webhookId)
                .eventType(webhookEvent)
                .issueKey(issueKey)
                .processed(false)
                .build();
        webhookEventRepository.save(record);

        // 4. Upsert JiraIssue
        Map<String, Object> fields = extractFields(payload);
        JiraIssue issue = upsertIssue(issueKey, summary, statusName, fields);

        // 4b. Changelog ledger + sprint membership (F3) — the previously-ignored
        // payload.changelog block feeds status/sprint/SP transitions; the Sprint
        // field value keeps the sprints table fresh in near-real-time.
        Object changelog = payload.get("changelog");
        if (changelog instanceof Map) {
            //noinspection unchecked
            Map<String, Object> history = new LinkedHashMap<>((Map<String, Object>) changelog);
            history.putIfAbsent("created", fields.get("updated"));
            issueTransitions.record(issue, issueTransitions.parseHistory(history));
        }
        jiraConfigRepository.findFirstByOrderByIdAsc().ifPresent(cfg -> {
            if (cfg.getSprintField() != null && !cfg.getSprintField().isBlank()) {
                sprintIngest.ingestFieldValue(issue, fields.get(cfg.getSprintField()));
            }
        });

        // 5. Async: classify issue with BERT triage
        bertTriageService.classifyIssue(issue);

        // 6. Async: notify DeliveryIntelligenceAgent
        deliveryAgent.onIssueEvent(issueKey);

        // Mark webhook as processed
        record.setProcessed(true);
        webhookEventRepository.save(record);

        log.info("JiraWebhookController: Processed event='{}' issue='{}' status='{}'",
                webhookEvent, issueKey, statusName);

        return ResponseEntity.ok().build();
    }

    // -------------------------------------------------------------------------
    // HMAC validation
    // -------------------------------------------------------------------------

    private boolean isSignatureValid(String header, String body) {
        // DB config takes precedence over application.yml value
        String webhookSecret = jiraConfigRepository.findFirstByOrderByIdAsc()
            .map(c -> c.getWebhookSecret())
            .filter(s -> s != null && !s.isBlank())
            .orElse(webhookSecretFallback);

        // Fail closed in prod: never accept the shipped default secret (M1).
        if ("dev-secret".equals(webhookSecret)
                && environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))) {
            log.error("SECURITY: refusing Jira webhook — webhook secret is the default 'dev-secret' in the prod profile");
            return false;
        }

        if (header == null || header.isBlank()) {
            log.warn("JiraWebhookController: Missing X-Hub-Signature-256 header — rejecting webhook");
            return false;
        }
        try {
            String algorithm = "HmacSHA256";
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] computed = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String computedHex = "sha256=" + HexFormat.of().formatHex(computed);
            return constantTimeEquals(computedHex, header.trim());
        } catch (Exception e) {
            log.error("JiraWebhookController: HMAC computation failed — {}", e.getMessage());
            return false;
        }
    }

    /** Constant-time string comparison to prevent timing attacks. */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    // -------------------------------------------------------------------------
    // Issue upsert
    // -------------------------------------------------------------------------

    private JiraIssue upsertIssue(String issueKey, String summary, String statusName,
                                  Map<String, Object> fields) {
        JiraIssue issue = jiraIssueRepository.findByIssueKey(issueKey).orElse(null);
        if (issue == null) {
            issue = new JiraIssue();
            issue.setIssueKey(issueKey);
            log.info("JiraWebhookController: Creating new JiraIssue '{}'", issueKey);
        } else {
            log.info("JiraWebhookController: Updating existing JiraIssue '{}'", issueKey);
        }
        if (summary != null && !summary.isBlank()) {
            issue.setSummary(summary);
        }
        if (statusName != null && !statusName.isBlank()) {
            issue.setJiraStatus(statusName);
        }
        // Jira's own timestamps; created_at/updated_at never hold processing time.
        // resolutiondate is only applied when present so a partial payload can't
        // clear it, but an explicit null (unresolved/reopened) does.
        LocalDateTime jiraCreated = JiraDates.parse(fields.get("created"));
        LocalDateTime jiraUpdated = JiraDates.parse(fields.get("updated"));
        if (jiraCreated != null) issue.setCreatedAt(jiraCreated);
        else if (issue.getCreatedAt() == null) issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(jiraUpdated != null ? jiraUpdated : LocalDateTime.now());
        if (fields.containsKey("resolutiondate")) {
            issue.setResolvedAt(JiraDates.parse(fields.get("resolutiondate")));
        }
        // Mapped custom fields (story points / Sprint / SM / PjM) — same parsing as JQL sync
        JiraIssue target = issue;
        jiraConfigRepository.findFirstByOrderByIdAsc()
            .ifPresent(cfg -> JiraFieldMapper.apply(target, fields, cfg));
        issue.setLastSyncedAt(LocalDateTime.now());
        return jiraIssueRepository.save(issue);
    }

    // -------------------------------------------------------------------------
    // JSON extraction helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private String extractIssueKey(Map<String, Object> payload) {
        Object issue = payload.get("issue");
        if (issue instanceof Map) {
            Object key = ((Map<String, Object>) issue).get("key");
            if (key instanceof String) return (String) key;
        }
        return "UNKNOWN-0";
    }

    @SuppressWarnings("unchecked")
    private String extractSummary(Map<String, Object> payload) {
        Object issue = payload.get("issue");
        if (issue instanceof Map) {
            Object fields = ((Map<String, Object>) issue).get("fields");
            if (fields instanceof Map) {
                Object summary = ((Map<String, Object>) fields).get("summary");
                if (summary instanceof String) return (String) summary;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFields(Map<String, Object> payload) {
        Object issue = payload.get("issue");
        if (issue instanceof Map) {
            Object fields = ((Map<String, Object>) issue).get("fields");
            if (fields instanceof Map) return (Map<String, Object>) fields;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private String extractStatusName(Map<String, Object> payload) {
        Object issue = payload.get("issue");
        if (issue instanceof Map) {
            Object fields = ((Map<String, Object>) issue).get("fields");
            if (fields instanceof Map) {
                Object status = ((Map<String, Object>) fields).get("status");
                if (status instanceof Map) {
                    Object name = ((Map<String, Object>) status).get("name");
                    if (name instanceof String) return (String) name;
                }
            }
        }
        return null;
    }
}
