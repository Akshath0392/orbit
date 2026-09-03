package com.orbit.domain.config;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Single-row (id=1) AM configuration: delivery-health pillar weights (mock ⚙
 * modal, admin-editable) and the external adoption-dashboard deep link (F4).
 */
@Entity
@Table(name = "am_settings")
public class AmSettings {

    @Id
    private Long id = 1L;

    @Column(name = "dh_speed_weight", nullable = false)
    private Integer dhSpeedWeight = 40;

    @Column(name = "dh_quality_weight", nullable = false)
    private Integer dhQualityWeight = 35;

    @Column(name = "dh_pred_weight", nullable = false)
    private Integer dhPredWeight = 25;

    @Column(name = "adoption_url", length = 512)
    private String adoptionUrl;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Integer getDhSpeedWeight() { return dhSpeedWeight; }
    public void setDhSpeedWeight(Integer v) { this.dhSpeedWeight = v; }
    public Integer getDhQualityWeight() { return dhQualityWeight; }
    public void setDhQualityWeight(Integer v) { this.dhQualityWeight = v; }
    public Integer getDhPredWeight() { return dhPredWeight; }
    public void setDhPredWeight(Integer v) { this.dhPredWeight = v; }
    public String getAdoptionUrl() { return adoptionUrl; }
    public void setAdoptionUrl(String v) { this.adoptionUrl = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
