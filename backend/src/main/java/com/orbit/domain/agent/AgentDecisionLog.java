package com.orbit.domain.agent;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name="agent_decision_log")
public class AgentDecisionLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String agentName;
    @Column(columnDefinition="TEXT") private String triggerEvent;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="proposal_json", columnDefinition="jsonb") private String proposalJson;
    private String outcome;
    @Column(columnDefinition="TEXT") private String outcomeNote;
    private Integer tokensUsed;
    private String decidedBy;
    private LocalDateTime decidedAt = LocalDateTime.now();

    public AgentDecisionLog() {}
    public Long getId() { return id; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String v) { this.agentName=v; }
    public String getTriggerEvent() { return triggerEvent; }
    public void setTriggerEvent(String v) { this.triggerEvent=v; }
    public String getProposalJson() { return proposalJson; }
    public void setProposalJson(String v) { this.proposalJson=v; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String v) { this.outcome=v; }
    public String getOutcomeNote() { return outcomeNote; }
    public void setOutcomeNote(String v) { this.outcomeNote=v; }
    public Integer getTokensUsed() { return tokensUsed; }
    public void setTokensUsed(Integer v) { this.tokensUsed=v; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String v) { this.decidedBy=v; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime v) { this.decidedAt=v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private AgentDecisionLog d = new AgentDecisionLog();
        public Builder agentName(String v) { d.agentName=v; return this; }
        public Builder triggerEvent(String v) { d.triggerEvent=v; return this; }
        public Builder proposalJson(String v) { d.proposalJson=v; return this; }
        public Builder outcome(String v) { d.outcome=v; return this; }
        public Builder outcomeNote(String v) { d.outcomeNote=v; return this; }
        public Builder tokensUsed(Integer v) { d.tokensUsed=v; return this; }
        public Builder decidedBy(String v) { d.decidedBy=v; return this; }
        public Builder decidedAt(LocalDateTime v) { d.decidedAt=v; return this; }
        public AgentDecisionLog build() { return d; }
    }
}
