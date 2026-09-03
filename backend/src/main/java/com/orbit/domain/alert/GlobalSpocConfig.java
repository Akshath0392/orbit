package com.orbit.domain.alert;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "global_spoc_config")
public class GlobalSpocConfig {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String spocType;
    private String email;
    private String name;
    private String slackUserId;
    private LocalDateTime updatedAt = LocalDateTime.now();

    public GlobalSpocConfig() {}

    public Long getId() { return id; }
    public String getSpocType() { return spocType; }
    public void setSpocType(String v) { this.spocType = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getSlackUserId() { return slackUserId; }
    public void setSlackUserId(String v) { this.slackUserId = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
