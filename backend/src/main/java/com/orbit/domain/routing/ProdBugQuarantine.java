package com.orbit.domain.routing;

import com.orbit.domain.issue.JiraIssue;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One row per Jira issue whose client_code was missing or unknown at sync time.
 * Kept idempotent via {@code jira_key} unique constraint — repeated syncs of
 * the same stuck bug bump {@code lastSeenAt} instead of creating duplicates.
 * See docs/plan/prod-bug-routing-plan.md.
 */
@Entity
@Table(name = "prod_bug_quarantine")
public class ProdBugQuarantine {

    public enum Reason {
        /** The Jira issue's client_code field was blank or absent. */
        MISSING_CODE,
        /** A code was present but doesn't map to any known client. */
        UNKNOWN_CODE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jira_issue_id")
    private JiraIssue jiraIssue;

    @Column(name = "jira_key", nullable = false, unique = true)
    private String jiraKey;

    @Column(name = "raw_client_code")
    private String rawClientCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Reason reason;

    @Column(name = "seen_at", nullable = false)
    private LocalDateTime seenAt = LocalDateTime.now();

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt = LocalDateTime.now();

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    public Long getId() { return id; }
    public JiraIssue getJiraIssue() { return jiraIssue; }
    public void setJiraIssue(JiraIssue v) { this.jiraIssue = v; }
    public String getJiraKey() { return jiraKey; }
    public void setJiraKey(String v) { this.jiraKey = v; }
    public String getRawClientCode() { return rawClientCode; }
    public void setRawClientCode(String v) { this.rawClientCode = v; }
    public Reason getReason() { return reason; }
    public void setReason(Reason v) { this.reason = v; }
    public LocalDateTime getSeenAt() { return seenAt; }
    public void setSeenAt(LocalDateTime v) { this.seenAt = v; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime v) { this.lastSeenAt = v; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime v) { this.resolvedAt = v; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String v) { this.resolvedBy = v; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String v) { this.resolutionNote = v; }

    public boolean isOpen() { return resolvedAt == null; }
}
