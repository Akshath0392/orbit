package com.orbit.domain.client;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name="man_day_budgets")
public class ManDayBudget {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id") private Project project;
    private BigDecimal purchasedDays;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal dailyRateHours = BigDecimal.valueOf(8.0);
    private Integer alertThresholdPct = 80;

    public ManDayBudget() {}
    public Long getId() { return id; }
    public Project getProject() { return project; }
    public void setProject(Project v) { this.project=v; }
    public BigDecimal getPurchasedDays() { return purchasedDays; }
    public void setPurchasedDays(BigDecimal v) { this.purchasedDays=v; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate v) { this.periodStart=v; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate v) { this.periodEnd=v; }
    public BigDecimal getDailyRateHours() { return dailyRateHours; }
    public Integer getAlertThresholdPct() { return alertThresholdPct; }
    public void setAlertThresholdPct(Integer v) { this.alertThresholdPct=v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private ManDayBudget b = new ManDayBudget();
        public Builder project(Project v) { b.project=v; return this; }
        public Builder purchasedDays(BigDecimal v) { b.purchasedDays=v; return this; }
        public Builder periodStart(LocalDate v) { b.periodStart=v; return this; }
        public Builder periodEnd(LocalDate v) { b.periodEnd=v; return this; }
        public Builder alertThresholdPct(Integer v) { b.alertThresholdPct=v; return this; }
        public ManDayBudget build() { return b; }
    }
}
