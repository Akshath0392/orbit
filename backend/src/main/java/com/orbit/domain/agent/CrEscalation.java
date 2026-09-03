package com.orbit.domain.agent;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Dedup ledger for the SLA-breach escalation loop (table added in V90). One row per
 * CR the scheduled sweep has proposed an escalation for; the sweep skips any CR
 * whose {@code lastProposedAt} is within the cooldown so a breach isn't re-nagged
 * every run. Keyed by the Jira issue key.
 */
@Entity
@Table(name = "cr_escalation")
public class CrEscalation {

    @Id
    @Column(name = "issue_key", length = 64)
    private String issueKey;

    @Column(name = "last_proposed_at", nullable = false)
    private LocalDateTime lastProposedAt;

    @Column(name = "last_outcome", length = 32)
    private String lastOutcome;

    @Column(name = "decision_log_id")
    private Long decisionLogId;

    protected CrEscalation() {}

    public CrEscalation(String issueKey, LocalDateTime lastProposedAt) {
        this.issueKey = issueKey;
        this.lastProposedAt = lastProposedAt;
    }

    public String getIssueKey() { return issueKey; }
    public LocalDateTime getLastProposedAt() { return lastProposedAt; }
    public void setLastProposedAt(LocalDateTime v) { this.lastProposedAt = v; }
    public String getLastOutcome() { return lastOutcome; }
    public void setLastOutcome(String v) { this.lastOutcome = v; }
    public Long getDecisionLogId() { return decisionLogId; }
    public void setDecisionLogId(Long v) { this.decisionLogId = v; }
}
