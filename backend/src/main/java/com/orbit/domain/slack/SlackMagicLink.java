package com.orbit.domain.slack;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "slack_magic_link")
public class SlackMagicLink {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 96)
    private String token;

    @Column(name = "slack_user_id", nullable = false, length = 64)
    private String slackUserId;

    @Column(nullable = false)
    private String email;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    public Long getId() { return id; }
    public String getToken() { return token; }
    public void setToken(String v) { this.token = v; }
    public String getSlackUserId() { return slackUserId; }
    public void setSlackUserId(String v) { this.slackUserId = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime v) { this.expiresAt = v; }
    public LocalDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(LocalDateTime v) { this.consumedAt = v; }
}
