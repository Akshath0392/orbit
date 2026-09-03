package com.orbit.domain.issue;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Sprint membership with add/remove times from Sprint-field changelog diffs (V86, F3). */
@Entity
@Table(name = "sprint_issues",
       uniqueConstraints = @UniqueConstraint(columnNames = {"sprint_id", "issue_id"}))
public class SprintIssue {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sprint_id", nullable = false)
    private Long sprintId;

    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Column(name = "added_at")
    private LocalDateTime addedAt;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    /** Member at sprint start (+15 min grace) — D4 committed semantics. */
    private Boolean committed;

    @Column(name = "committed_story_points")
    private BigDecimal committedStoryPoints;

    public Long getId() { return id; }
    public Long getSprintId() { return sprintId; }
    public void setSprintId(Long v) { this.sprintId = v; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long v) { this.issueId = v; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime v) { this.addedAt = v; }
    public LocalDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(LocalDateTime v) { this.removedAt = v; }
    public Boolean getCommitted() { return committed; }
    public void setCommitted(Boolean v) { this.committed = v; }
    public BigDecimal getCommittedStoryPoints() { return committedStoryPoints; }
    public void setCommittedStoryPoints(BigDecimal v) { this.committedStoryPoints = v; }
}
