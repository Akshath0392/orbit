package com.orbit.domain.issue;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persists each inbound Jira webhook call for idempotency and audit.
 * Maps to the {@code jira_webhook_events} table created by Flyway V32.
 */
@Entity
@Table(name = "jira_webhook_events",
       uniqueConstraints = @UniqueConstraint(name = "uq_jira_webhook_id", columnNames = "webhook_id"))
public class JiraWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "webhook_id", length = 100, nullable = false, unique = true)
    private String webhookId;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "issue_key", length = 50)
    private String issueKey;

    @Column(name = "received_at")
    private LocalDateTime receivedAt = LocalDateTime.now();

    private Boolean processed = false;

    public JiraWebhookEvent() {}

    public Long getId() { return id; }

    public String getWebhookId() { return webhookId; }
    public void setWebhookId(String v) { this.webhookId = v; }

    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }

    public String getIssueKey() { return issueKey; }
    public void setIssueKey(String v) { this.issueKey = v; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime v) { this.receivedAt = v; }

    public Boolean getProcessed() { return processed; }
    public void setProcessed(Boolean v) { this.processed = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final JiraWebhookEvent e = new JiraWebhookEvent();
        public Builder webhookId(String v) { e.webhookId = v; return this; }
        public Builder eventType(String v) { e.eventType = v; return this; }
        public Builder issueKey(String v) { e.issueKey = v; return this; }
        public Builder processed(Boolean v) { e.processed = v; return this; }
        public JiraWebhookEvent build() { return e; }
    }
}
