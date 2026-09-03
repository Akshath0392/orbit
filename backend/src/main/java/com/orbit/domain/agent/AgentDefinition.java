package com.orbit.domain.agent;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_definitions")
public class AgentDefinition {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String agentType;
    private String triggerType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String triggerConfig;

    @Column(columnDefinition = "TEXT")
    private String promptTemplate;

    // Store tools as a comma-separated string to avoid Hibernate array type issues
    @Column(name = "tools", columnDefinition = "TEXT")
    private String toolsCsv;

    private String outputChannel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String channelConfig;

    private Boolean requiresHitl = true;
    private Boolean enabled = false;
    private Long projectId;
    private Boolean systemAgent = false;

    @Column(name = "slack_exposed")
    private Boolean slackExposed = false;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AgentDefinition() {}

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    public String getAgentType() { return agentType; }
    public void setAgentType(String v) { this.agentType = v; }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String v) { this.triggerType = v; }

    public String getTriggerConfig() { return triggerConfig; }
    public void setTriggerConfig(String v) { this.triggerConfig = v; }

    public String getPromptTemplate() { return promptTemplate; }
    public void setPromptTemplate(String v) { this.promptTemplate = v; }

    /** Returns the raw tools array from the DB column. */
    @Transient
    public String[] getTools() {
        if (toolsCsv == null || toolsCsv.isBlank()) return new String[0];
        return toolsCsv.split(",");
    }

    /** Accepts a String[] and joins it for storage. */
    public void setTools(String[] tools) {
        this.toolsCsv = tools == null ? null : String.join(",", tools);
    }

    public String getToolsCsv() { return toolsCsv; }
    public void setToolsCsv(String v) { this.toolsCsv = v; }

    public String getOutputChannel() { return outputChannel; }
    public void setOutputChannel(String v) { this.outputChannel = v; }

    public String getChannelConfig() { return channelConfig; }
    public void setChannelConfig(String v) { this.channelConfig = v; }

    public Boolean getRequiresHitl() { return requiresHitl; }
    public void setRequiresHitl(Boolean v) { this.requiresHitl = v; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean v) { this.enabled = v; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }

    public Boolean getSystemAgent() { return systemAgent; }
    public void setSystemAgent(Boolean v) { this.systemAgent = v; }

    public Boolean getSlackExposed() { return slackExposed; }
    public void setSlackExposed(Boolean v) { this.slackExposed = v; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
