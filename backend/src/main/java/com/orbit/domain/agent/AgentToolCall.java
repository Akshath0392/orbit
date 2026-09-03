package com.orbit.domain.agent;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_tool_calls")
public class AgentToolCall {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long runId;
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String args;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String result;

    private Boolean hitlRequired = false;
    private String hitlOutcome;

    @Column(columnDefinition = "TEXT")
    private String hitlNote;

    private LocalDateTime calledAt;

    public AgentToolCall() {}

    public Long getId() { return id; }

    public Long getRunId() { return runId; }
    public void setRunId(Long v) { this.runId = v; }

    public String getToolName() { return toolName; }
    public void setToolName(String v) { this.toolName = v; }

    public String getArgs() { return args; }
    public void setArgs(String v) { this.args = v; }

    public String getResult() { return result; }
    public void setResult(String v) { this.result = v; }

    public Boolean getHitlRequired() { return hitlRequired; }
    public void setHitlRequired(Boolean v) { this.hitlRequired = v; }

    public String getHitlOutcome() { return hitlOutcome; }
    public void setHitlOutcome(String v) { this.hitlOutcome = v; }

    public String getHitlNote() { return hitlNote; }
    public void setHitlNote(String v) { this.hitlNote = v; }

    public LocalDateTime getCalledAt() { return calledAt; }
    public void setCalledAt(LocalDateTime v) { this.calledAt = v; }
}
