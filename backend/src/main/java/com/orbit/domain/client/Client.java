package com.orbit.domain.client;

import jakarta.persistence.*;

@Entity
@Table(name="clients")
public class Client {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String name;
    private String code;
    private Integer healthGreenThreshold = 80;
    private Integer healthAmberThreshold = 60;
    private String contactName;
    private Boolean active = true;
    // Admin-entered CSAT split by engagement type (V85, interim until a survey
    // feed exists) + composite engagement score. 1–10 scale per the mock.
    private java.math.BigDecimal csatLaunch;
    private java.math.BigDecimal csatBau;
    private Integer engagementScore;

    public Client() {}
    public java.math.BigDecimal getCsatLaunch() { return csatLaunch; }
    public void setCsatLaunch(java.math.BigDecimal v) { this.csatLaunch=v; }
    public java.math.BigDecimal getCsatBau() { return csatBau; }
    public void setCsatBau(java.math.BigDecimal v) { this.csatBau=v; }
    public Integer getEngagementScore() { return engagementScore; }
    public void setEngagementScore(Integer v) { this.engagementScore=v; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name=v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code=v; }
    public Integer getHealthGreenThreshold() { return healthGreenThreshold; }
    public void setHealthGreenThreshold(Integer v) { this.healthGreenThreshold=v; }
    public Integer getHealthAmberThreshold() { return healthAmberThreshold; }
    public void setHealthAmberThreshold(Integer v) { this.healthAmberThreshold=v; }
    public String getContactName() { return contactName; }
    public void setContactName(String v) { this.contactName=v; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { this.active=v; }
}
