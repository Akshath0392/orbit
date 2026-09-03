package com.orbit.domain.report;

import com.orbit.domain.client.Client;
import jakarta.persistence.*;
import org.hibernate.annotations.Array;
import java.time.LocalDateTime;

@Entity
@Table(name="report_schedules")
public class ReportSchedule {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String reportType;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="client_id") private Client client;
    private String cronExpression;
    @Column(columnDefinition="text[]") private String[] recipients;
    private Boolean includeClientSafeFilter = true;
    private Boolean active = true;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;

    public ReportSchedule() {}
    public Long getId() { return id; }
    public String getReportType() { return reportType; }
    public void setReportType(String v) { this.reportType=v; }
    public Client getClient() { return client; }
    public void setClient(Client v) { this.client=v; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String v) { this.cronExpression=v; }
    public String[] getRecipients() { return recipients; }
    public void setRecipients(String[] v) { this.recipients=v; }
    public Boolean getIncludeClientSafeFilter() { return includeClientSafeFilter; }
    public void setIncludeClientSafeFilter(Boolean v) { this.includeClientSafeFilter=v; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { this.active=v; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime v) { this.lastRunAt=v; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime v) { this.nextRunAt=v; }
}
