package com.orbit.domain.issue;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Jira sprint, upserted from the issue Sprint custom-field payload (V86, F3). */
@Entity
@Table(name = "sprints")
public class Sprint {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jira_sprint_id", nullable = false, unique = true)
    private Long jiraSprintId;

    @Column(name = "board_id")
    private Long boardId;

    private String name;

    private String state; // future | active | closed

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "complete_date")
    private LocalDateTime completeDate;

    @Column(columnDefinition = "TEXT")
    private String goal;

    /** Set when the future→active snapshot ran; NULL → committed SP is approximate. */
    @Column(name = "committed_snapshot_at")
    private LocalDateTime committedSnapshotAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    public Long getId() { return id; }
    public Long getJiraSprintId() { return jiraSprintId; }
    public void setJiraSprintId(Long v) { this.jiraSprintId = v; }
    public Long getBoardId() { return boardId; }
    public void setBoardId(Long v) { this.boardId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime v) { this.startDate = v; }
    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime v) { this.endDate = v; }
    public LocalDateTime getCompleteDate() { return completeDate; }
    public void setCompleteDate(LocalDateTime v) { this.completeDate = v; }
    public String getGoal() { return goal; }
    public void setGoal(String v) { this.goal = v; }
    public LocalDateTime getCommittedSnapshotAt() { return committedSnapshotAt; }
    public void setCommittedSnapshotAt(LocalDateTime v) { this.committedSnapshotAt = v; }
    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(LocalDateTime v) { this.lastSyncedAt = v; }
}
