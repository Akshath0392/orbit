package com.orbit.domain.config;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "jira_sync_runs")
public class JiraSyncRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String syncType;
    private String status;
    private Integer issuesProcessed;
    private Integer durationMs;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    // V99 observability: attribution + live progress + project scope
    private Long projectId;
    private String triggeredBy;
    private Integer totalExpected;
    private Integer processedSoFar;
    @Column(columnDefinition = "TEXT")
    private String projectScope;
    private String currentProject;

    public JiraSyncRun() {}
    public Long getProjectId()                     { return projectId; }
    public void setProjectId(Long v)               { this.projectId = v; }
    public String getTriggeredBy()                 { return triggeredBy; }
    public void setTriggeredBy(String v)           { this.triggeredBy = v; }
    public Integer getTotalExpected()              { return totalExpected; }
    public void setTotalExpected(Integer v)        { this.totalExpected = v; }
    public Integer getProcessedSoFar()             { return processedSoFar; }
    public void setProcessedSoFar(Integer v)       { this.processedSoFar = v; }
    public String getProjectScope()                { return projectScope; }
    public void setProjectScope(String v)          { this.projectScope = v; }
    public String getCurrentProject()              { return currentProject; }
    public void setCurrentProject(String v)        { this.currentProject = v; }
    public Long getId()                            { return id; }
    public String getSyncType()                    { return syncType; }
    public void setSyncType(String v)              { this.syncType = v; }
    public String getStatus()                      { return status; }
    public void setStatus(String v)                { this.status = v; }
    public Integer getIssuesProcessed()            { return issuesProcessed; }
    public void setIssuesProcessed(Integer v)      { this.issuesProcessed = v; }
    public Integer getDurationMs()                 { return durationMs; }
    public void setDurationMs(Integer v)           { this.durationMs = v; }
    public String getErrorMessage()                { return errorMessage; }
    public void setErrorMessage(String v)          { this.errorMessage = v; }
    public LocalDateTime getStartedAt()            { return startedAt; }
    public void setStartedAt(LocalDateTime v)      { this.startedAt = v; }
    public LocalDateTime getCompletedAt()          { return completedAt; }
    public void setCompletedAt(LocalDateTime v)    { this.completedAt = v; }
}
