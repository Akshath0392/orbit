package com.orbit.domain.client;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name="sla_rules")
public class SlaRule {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="client_id") private Client client;
    private String severity;
    private BigDecimal responseHours;
    private BigDecimal resolutionHours;
    private Boolean includeWeekends = false;

    public SlaRule() {}
    public Long getId() { return id; }
    public Client getClient() { return client; }
    public void setClient(Client v) { this.client=v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity=v; }
    public BigDecimal getResponseHours() { return responseHours; }
    public void setResponseHours(BigDecimal v) { this.responseHours=v; }
    public BigDecimal getResolutionHours() { return resolutionHours; }
    public void setResolutionHours(BigDecimal v) { this.resolutionHours=v; }
    public Boolean getIncludeWeekends() { return includeWeekends; }
    public void setIncludeWeekends(Boolean v) { this.includeWeekends=v; }
}
