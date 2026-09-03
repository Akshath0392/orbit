package com.orbit.domain.alert;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_rules")
public class NotificationRule {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String ruleName;
    private String triggerType;
    private String role;
    private String phase;
    private Integer offsetDays = 0;
    private String triggerTime = "09:00";
    private Boolean enabled = true;
    private Integer overdueIntervalHours = 3;
    private String overdueWindowStart = "09:00";
    private String overdueWindowEnd = "21:00";
    private String templateId;
    private LocalDateTime createdAt = LocalDateTime.now();

    public NotificationRule() {}

    public Long getId() { return id; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String v) { this.ruleName = v; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String v) { this.triggerType = v; }
    public String getRole() { return role; }
    public void setRole(String v) { this.role = v; }
    public String getPhase() { return phase; }
    public void setPhase(String v) { this.phase = v; }
    public Integer getOffsetDays() { return offsetDays; }
    public void setOffsetDays(Integer v) { this.offsetDays = v; }
    public String getTriggerTime() { return triggerTime; }
    public void setTriggerTime(String v) { this.triggerTime = v; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean v) { this.enabled = v; }
    public Integer getOverdueIntervalHours() { return overdueIntervalHours; }
    public void setOverdueIntervalHours(Integer v) { this.overdueIntervalHours = v; }
    public String getOverdueWindowStart() { return overdueWindowStart; }
    public void setOverdueWindowStart(String v) { this.overdueWindowStart = v; }
    public String getOverdueWindowEnd() { return overdueWindowEnd; }
    public void setOverdueWindowEnd(String v) { this.overdueWindowEnd = v; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String v) { this.templateId = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
