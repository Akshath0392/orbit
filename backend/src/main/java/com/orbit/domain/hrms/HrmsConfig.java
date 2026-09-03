package com.orbit.domain.hrms;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single-row HRMS connector configuration: which provider is active plus its
 * provider-specific settings (shape defined by the connector's descriptor).
 * Secrets live inside the settings blob; the API only ever echoes a set/unset flag.
 */
@Entity
@Table(name = "hrms_config")
public class HrmsConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "provider_key") private String providerKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> settings = new LinkedHashMap<>();

    private Boolean enabled = false;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by") private String updatedBy;

    public HrmsConfig() {}
    public Long getId() { return id; }
    public String getProviderKey() { return providerKey; }
    public void setProviderKey(String v) { this.providerKey = v; }
    public Map<String, Object> getSettings() { return settings; }
    public void setSettings(Map<String, Object> v) { this.settings = v; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean v) { this.enabled = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
}
