package com.orbit.domain.capacity;

import com.orbit.domain.client.Project;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name="man_day_snapshots")
public class ManDaySnapshot {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id") private Project project;
    private LocalDate snapshotDate;
    private BigDecimal burnedDays;
    private BigDecimal remainingDays;
    private BigDecimal burnRatePerDay;
    private LocalDate forecastExhaustion;

    public ManDaySnapshot() {}
    public Long getId() { return id; }
    public Project getProject() { return project; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public BigDecimal getBurnedDays() { return burnedDays; }
    public BigDecimal getRemainingDays() { return remainingDays; }
    public BigDecimal getBurnRatePerDay() { return burnRatePerDay; }
    public LocalDate getForecastExhaustion() { return forecastExhaustion; }
}
