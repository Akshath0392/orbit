package com.orbit.domain.hrms;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "hrms_sync_runs")
public class HrmsSyncRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String syncType;
    private String status;
    private Integer recordsPulled = 0;
    private String errorMessage;
    private LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime completedAt;

    public HrmsSyncRun() {}
    public Long getId() { return id; }
    public String getSyncType() { return syncType; }
    public void setSyncType(String v) { this.syncType = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Integer getRecordsPulled() { return recordsPulled; }
    public void setRecordsPulled(Integer v) { this.recordsPulled = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime v) { this.startedAt = v; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime v) { this.completedAt = v; }
}
