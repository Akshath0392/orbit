package com.orbit.domain.agent;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_runs")
public class AgentRun {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long agentId;
    private Long projectId;
    private String triggeredBy;
    private String status = "RUNNING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String inputContext;

    @Column(columnDefinition = "TEXT")
    private String outputSummary;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Integer tokensUsed;
    private Integer durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Column(name = "invocation_source")
    private String invocationSource = "SCHEDULED";

    public AgentRun() {}

    public Long getId() { return id; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long v) { this.agentId = v; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String v) { this.triggeredBy = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public String getInputContext() { return inputContext; }
    public void setInputContext(String v) { this.inputContext = v; }

    public String getOutputSummary() { return outputSummary; }
    public void setOutputSummary(String v) { this.outputSummary = v; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }

    public Integer getTokensUsed() { return tokensUsed; }
    public void setTokensUsed(Integer v) { this.tokensUsed = v; }

    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer v) { this.durationMs = v; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime v) { this.startedAt = v; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime v) { this.completedAt = v; }

    public String getInvocationSource() { return invocationSource; }
    public void setInvocationSource(String v) { this.invocationSource = v; }
}
