package com.orbit.domain.config;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "slack_config")
public class SlackConfig {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String workspaceName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String botToken;

    @Column(columnDefinition = "TEXT")
    private String signingSecret;

    private String defaultChannel;

    private Boolean enabled = true;

    private LocalDateTime createdAt;

    public SlackConfig() {}

    public Long getId() { return id; }

    public String getWorkspaceName() { return workspaceName; }
    public void setWorkspaceName(String v) { this.workspaceName = v; }

    public String getBotToken() { return botToken; }
    public void setBotToken(String v) { this.botToken = v; }

    public String getSigningSecret() { return signingSecret; }
    public void setSigningSecret(String v) { this.signingSecret = v; }

    public String getDefaultChannel() { return defaultChannel; }
    public void setDefaultChannel(String v) { this.defaultChannel = v; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean v) { this.enabled = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
