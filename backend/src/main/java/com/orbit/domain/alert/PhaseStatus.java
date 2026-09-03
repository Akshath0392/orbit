package com.orbit.domain.alert;

import com.orbit.domain.client.Project;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "phase_statuses")
public class PhaseStatus {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    private String phase;
    private LocalDate startDate;
    private LocalDate endDate;
    private String assigneeEmail;
    private String assigneeName;
    private String status = "NOT_STARTED";
    @Column(columnDefinition = "TEXT") private String delayNote;
    private String jiraIssueKey;
    @Column(name = "last_notified_t2") private LocalDate lastNotifiedT2;
    @Column(name = "last_notified_t1") private LocalDate lastNotifiedT1;
    private Boolean ddayNotified = false;
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PhaseStatus() {}

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public void setProject(Project v) { this.project = v; }
    public String getPhase() { return phase; }
    public void setPhase(String v) { this.phase = v; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { this.endDate = v; }
    public String getAssigneeEmail() { return assigneeEmail; }
    public void setAssigneeEmail(String v) { this.assigneeEmail = v; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String v) { this.assigneeName = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getDelayNote() { return delayNote; }
    public void setDelayNote(String v) { this.delayNote = v; }
    public String getJiraIssueKey() { return jiraIssueKey; }
    public void setJiraIssueKey(String v) { this.jiraIssueKey = v; }
    public LocalDate getLastNotifiedT2() { return lastNotifiedT2; }
    public void setLastNotifiedT2(LocalDate v) { this.lastNotifiedT2 = v; }
    public LocalDate getLastNotifiedT1() { return lastNotifiedT1; }
    public void setLastNotifiedT1(LocalDate v) { this.lastNotifiedT1 = v; }
    public Boolean getDdayNotified() { return ddayNotified; }
    public void setDdayNotified(Boolean v) { this.ddayNotified = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
