package com.orbit.domain.config;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "health_profile_weights")
public class HealthProfileWeight {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String stage;          // PRE_LAUNCH | HYPERCARE | STEADY_STATE | AT_RISK
    private String metric;         // prod_bug_p0 | prod_bug_p1 | sla_breach | cr_on_hold_pct | uat_bug_count | manday_burn_risk
    private Integer weight = 0;    // max deduction this metric can contribute (0–100)
    private BigDecimal sensitivity = BigDecimal.ONE; // normalization speed

    public HealthProfileWeight() {}
    public Long getId() { return id; }
    public String getStage() { return stage; }
    public void setStage(String v) { this.stage = v; }
    public String getMetric() { return metric; }
    public void setMetric(String v) { this.metric = v; }
    public Integer getWeight() { return weight; }
    public void setWeight(Integer v) { this.weight = v; }
    public BigDecimal getSensitivity() { return sensitivity; }
    public void setSensitivity(BigDecimal v) { this.sensitivity = v; }
}
