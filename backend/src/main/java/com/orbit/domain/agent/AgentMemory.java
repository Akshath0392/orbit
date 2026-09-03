package com.orbit.domain.agent;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_memory")
public class AgentMemory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long agentId;
    private Long projectId;
    private String memoryType = "FACT";

    @Column(name = "mem_key")
    private String memKey;

    @Column(name = "mem_value", columnDefinition = "TEXT")
    private String memValue;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    public AgentMemory() {}

    public Long getId() { return id; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long v) { this.agentId = v; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }

    public String getMemoryType() { return memoryType; }
    public void setMemoryType(String v) { this.memoryType = v; }

    public String getMemKey() { return memKey; }
    public void setMemKey(String v) { this.memKey = v; }

    public String getMemValue() { return memValue; }
    public void setMemValue(String v) { this.memValue = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime v) { this.expiresAt = v; }
}
