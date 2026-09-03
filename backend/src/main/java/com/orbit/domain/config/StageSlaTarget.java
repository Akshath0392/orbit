package com.orbit.domain.config;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stage_sla_targets")
public class StageSlaTarget {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stage", nullable = false, unique = true, length = 64)
    private String stage;

    @Column(name = "target_days", nullable = false)
    private Integer targetDays;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getStage() { return stage; }
    public void setStage(String v) { this.stage = v; }
    public Integer getTargetDays() { return targetDays; }
    public void setTargetDays(Integer v) { this.targetDays = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
