package com.orbit.domain.issue;

import com.orbit.domain.client.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="jira_issues")
public class JiraIssue {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String issueKey;
    @Column(columnDefinition="TEXT") private String summary;
    private String issueType;
    private String jiraStatus;
    private String lifecycleStage;
    private String priority;
    private String severity;
    private String assigneeName;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id") private Project project;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="client_id") private Client client;
    private String fixVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime lastSyncedAt;
    private Integer reopenCount = 0;
    private String holdReason;
    // Mapped Jira custom fields (V85; populated only when jira_config maps them)
    private BigDecimal storyPoints;
    private String smOwner;
    private String pjmOwner;
    private Long currentSprintId;
    private String currentSprintName;
    private String developerName;
    // Standard Jira reporter (V98) — who raised the issue
    private String reporterName;
    private String reporterEmail;
    // Changelog-derived (V86; recomputed from the issue_transitions ledger)
    private LocalDateTime firstInProgressAt;
    private LocalDateTime changelogSyncedAt;
    private BigDecimal slaRemainingHours;
    private String slaStatus;
    private String bertSuggestedSeverity;
    private String bertSuggestedOwner;
    private Boolean bertSuggestionAccepted;

    public JiraIssue() {}
    public Long getId() { return id; }
    public String getIssueKey() { return issueKey; }
    public void setIssueKey(String v) { this.issueKey=v; }
    public String getSummary() { return summary; }
    public void setSummary(String v) { this.summary=v; }
    public String getIssueType() { return issueType; }
    public void setIssueType(String v) { this.issueType=v; }
    public String getJiraStatus() { return jiraStatus; }
    public void setJiraStatus(String v) { this.jiraStatus=v; }
    public String getLifecycleStage() { return lifecycleStage; }
    public void setLifecycleStage(String v) { this.lifecycleStage=v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority=v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity=v; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String v) { this.assigneeName=v; }
    public Project getProject() { return project; }
    public void setProject(Project v) { this.project=v; }
    public Client getClient() { return client; }
    public void setClient(Client v) { this.client=v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt=v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt=v; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime v) { this.resolvedAt=v; }
    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(LocalDateTime v) { this.lastSyncedAt=v; }
    public Integer getReopenCount() { return reopenCount; }
    public void setReopenCount(Integer v) { this.reopenCount=v; }
    public BigDecimal getSlaRemainingHours() { return slaRemainingHours; }
    public void setSlaRemainingHours(BigDecimal v) { this.slaRemainingHours=v; }
    public String getSlaStatus() { return slaStatus; }
    public void setSlaStatus(String v) { this.slaStatus=v; }
    public String getBertSuggestedSeverity() { return bertSuggestedSeverity; }
    public void setBertSuggestedSeverity(String v) { this.bertSuggestedSeverity=v; }
    public String getBertSuggestedOwner() { return bertSuggestedOwner; }
    public void setBertSuggestedOwner(String v) { this.bertSuggestedOwner=v; }
    public Boolean getBertSuggestionAccepted() { return bertSuggestionAccepted; }
    public void setBertSuggestionAccepted(Boolean v) { this.bertSuggestionAccepted=v; }
    public String getHoldReason() { return holdReason; }
    public BigDecimal getStoryPoints() { return storyPoints; }
    public void setStoryPoints(BigDecimal v) { this.storyPoints=v; }
    public String getSmOwner() { return smOwner; }
    public void setSmOwner(String v) { this.smOwner=v; }
    public String getPjmOwner() { return pjmOwner; }
    public void setPjmOwner(String v) { this.pjmOwner=v; }
    public Long getCurrentSprintId() { return currentSprintId; }
    public void setCurrentSprintId(Long v) { this.currentSprintId=v; }
    public String getCurrentSprintName() { return currentSprintName; }
    public void setCurrentSprintName(String v) { this.currentSprintName=v; }
    public String getDeveloperName() { return developerName; }
    public void setDeveloperName(String v) { this.developerName=v; }
    public String getReporterName() { return reporterName; }
    public void setReporterName(String v) { this.reporterName=v; }
    public String getReporterEmail() { return reporterEmail; }
    public void setReporterEmail(String v) { this.reporterEmail=v; }
    public LocalDateTime getFirstInProgressAt() { return firstInProgressAt; }
    public void setFirstInProgressAt(LocalDateTime v) { this.firstInProgressAt=v; }
    public LocalDateTime getChangelogSyncedAt() { return changelogSyncedAt; }
    public void setChangelogSyncedAt(LocalDateTime v) { this.changelogSyncedAt=v; }
}
