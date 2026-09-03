package com.orbit.service.agent.tool;

public class AgentRunContext {
    private Long agentId;
    private Long runId;
    private Long projectId;
    private String triggeredBy;

    public AgentRunContext() {}

    public AgentRunContext(Long agentId, Long runId, Long projectId, String triggeredBy) {
        this.agentId = agentId;
        this.runId = runId;
        this.projectId = projectId;
        this.triggeredBy = triggeredBy;
    }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long v) { this.agentId = v; }

    public Long getRunId() { return runId; }
    public void setRunId(Long v) { this.runId = v; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String v) { this.triggeredBy = v; }
}
