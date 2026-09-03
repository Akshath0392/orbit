package com.orbit.domain.alert;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "escalation_config")
public class EscalationConfig {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String role;
    private String phase;
    private String phaseSpocEmail;
    private String phaseSpocName;
    private Boolean deliverySpocEnabled = true;
    private Integer reEscalationHours = 24;
    private LocalDateTime updatedAt = LocalDateTime.now();

    public EscalationConfig() {}

    public Long getId() { return id; }
    public String getRole() { return role; }
    public void setRole(String v) { this.role = v; }
    public String getPhase() { return phase; }
    public void setPhase(String v) { this.phase = v; }
    public String getPhaseSpocEmail() { return phaseSpocEmail; }
    public void setPhaseSpocEmail(String v) { this.phaseSpocEmail = v; }
    public String getPhaseSpocName() { return phaseSpocName; }
    public void setPhaseSpocName(String v) { this.phaseSpocName = v; }
    public Boolean getDeliverySpocEnabled() { return deliverySpocEnabled; }
    public void setDeliverySpocEnabled(Boolean v) { this.deliverySpocEnabled = v; }
    public Integer getReEscalationHours() { return reEscalationHours; }
    public void setReEscalationHours(Integer v) { this.reEscalationHours = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
