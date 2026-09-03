package com.orbit.domain.issue;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Generic field-change ledger row (V21 table reshaped by V86). One row per
 * Jira changelog entry per tracked field; UNIQUE(issue_id, changelog_id,
 * field_type) makes webhook + backfill mutually idempotent. Derived values
 * (first_in_progress_at, reopen_count) are recomputed from this ledger,
 * never incremented.
 */
@Entity
@Table(name = "issue_transitions")
public class IssueTransition {

    public static final String STATUS = "status";
    public static final String SPRINT = "sprint";
    public static final String STORY_POINTS = "story_points";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_id")
    private Long issueId;

    // legacy V21 columns, still populated for status rows
    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status")
    private String toStatus;

    @Column(name = "transitioned_at")
    private LocalDateTime transitionedAt;

    @Column(name = "transitioned_by")
    private String transitionedBy;

    @Column(name = "field_type", nullable = false)
    private String fieldType = STATUS;

    @Column(name = "from_value", columnDefinition = "TEXT")
    private String fromValue;

    @Column(name = "to_value", columnDefinition = "TEXT")
    private String toValue;

    @Column(name = "changelog_id")
    private String changelogId;

    public Long getId() { return id; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long v) { this.issueId = v; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String v) { this.fromStatus = v; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String v) { this.toStatus = v; }
    public LocalDateTime getTransitionedAt() { return transitionedAt; }
    public void setTransitionedAt(LocalDateTime v) { this.transitionedAt = v; }
    public String getTransitionedBy() { return transitionedBy; }
    public void setTransitionedBy(String v) { this.transitionedBy = v; }
    public String getFieldType() { return fieldType; }
    public void setFieldType(String v) { this.fieldType = v; }
    public String getFromValue() { return fromValue; }
    public void setFromValue(String v) { this.fromValue = v; }
    public String getToValue() { return toValue; }
    public void setToValue(String v) { this.toValue = v; }
    public String getChangelogId() { return changelogId; }
    public void setChangelogId(String v) { this.changelogId = v; }
}
