package com.orbit.domain.config;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "feature_flags")
public class FeatureFlag {
    public static final String AUDIENCE_ALL = "ALL";
    public static final String AUDIENCE_PILOT = "PILOT";
    public static final String AUDIENCE_NONE = "NONE";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "flag_key", unique = true, nullable = false)
    private String flagKey;

    private String description;

    @Column(nullable = false)
    private String audience = AUDIENCE_ALL;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pilot_emails", columnDefinition = "jsonb")
    private List<String> pilotEmails = new ArrayList<>();

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public FeatureFlag() {}

    public Long getId() { return id; }
    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String v) { this.flagKey = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getAudience() { return audience; }
    public void setAudience(String v) { this.audience = v; }
    public List<String> getPilotEmails() { return pilotEmails; }
    public void setPilotEmails(List<String> v) { this.pilotEmails = v != null ? v : new ArrayList<>(); }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
