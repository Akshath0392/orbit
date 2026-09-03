package com.orbit.domain.config;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "jira_config")
public class JiraConfig {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_url")
    private String baseUrl;

    private String email;

    @Column(name = "api_token", length = 2000)
    private String apiToken;

    @Column(name = "webhook_secret")
    private String webhookSecret;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "sla_field")
    private String slaField;   // optional Jira custom field for SLA (e.g. customfield_10020 for JSM)

    // Optional custom-field mappings (V85, e.g. customfield_10016) — same
    // pattern as sla_field. Blank = feature stays dark, never wrong data.
    @Column(name = "story_points_field")
    private String storyPointsField;

    @Column(name = "sprint_field")
    private String sprintField;

    @Column(name = "sm_field")
    private String smField;    // Solutioning Manager

    @Column(name = "pjm_field")
    private String pjmField;   // Project Manager (PjM)

    @Column(name = "developer_field")
    private String developerField;   // Developer (user picker)

    public JiraConfig() {}

    public String getDeveloperField()          { return developerField; }
    public void setDeveloperField(String v)    { this.developerField = v; }

    public String getStoryPointsField()        { return storyPointsField; }
    public void setStoryPointsField(String v)  { this.storyPointsField = v; }
    public String getSprintField()             { return sprintField; }
    public void setSprintField(String v)       { this.sprintField = v; }
    public String getSmField()                 { return smField; }
    public void setSmField(String v)           { this.smField = v; }
    public String getPjmField()                { return pjmField; }
    public void setPjmField(String v)          { this.pjmField = v; }

    public Long getId()                   { return id; }
    public String getBaseUrl()            { return baseUrl; }
    public void setBaseUrl(String v)      { this.baseUrl = v; }
    public String getEmail()              { return email; }
    public void setEmail(String v)        { this.email = v; }
    public String getApiToken()           { return apiToken; }
    public void setApiToken(String v)     { this.apiToken = v; }
    public String getWebhookSecret()      { return webhookSecret; }
    public void setWebhookSecret(String v){ this.webhookSecret = v; }
    public LocalDateTime getUpdatedAt()   { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
    public String getUpdatedBy()          { return updatedBy; }
    public void setUpdatedBy(String v)    { this.updatedBy = v; }
    public String getSlaField()           { return slaField; }
    public void setSlaField(String v)     { this.slaField = v; }
}
