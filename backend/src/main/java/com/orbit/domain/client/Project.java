package com.orbit.domain.client;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name="projects")
public class Project {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String name;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="client_id") private Client client;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="portfolio_id") private Portfolio portfolio;
    private Boolean active = true;
    private String jiraProjectKeys;
    private String jiraJqlOverride;
    private String jiraCrFilter;
    private String jiraBugFilter;
    private LocalDate goLiveDate;
    private String healthStage;   // null = auto-inferred; PRE_LAUNCH | HYPERCARE | STEADY_STATE | AT_RISK

    public Project() {}
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name=v; }
    public Client getClient() { return client; }
    public void setClient(Client v) { this.client=v; }
    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio v) { this.portfolio=v; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { this.active=v; }
    public String getJiraProjectKeys() { return jiraProjectKeys; }
    public void setJiraProjectKeys(String v) { this.jiraProjectKeys=v; }
    public String getJiraJqlOverride() { return jiraJqlOverride; }
    public void setJiraJqlOverride(String v) { this.jiraJqlOverride=v; }
    public String getJiraCrFilter() { return jiraCrFilter; }
    public void setJiraCrFilter(String v) { this.jiraCrFilter=v; }
    public String getJiraBugFilter() { return jiraBugFilter; }
    public void setJiraBugFilter(String v) { this.jiraBugFilter=v; }
    public LocalDate getGoLiveDate() { return goLiveDate; }
    public void setGoLiveDate(LocalDate v) { this.goLiveDate=v; }
    public String getHealthStage() { return healthStage; }
    public void setHealthStage(String v) { this.healthStage=v; }

    private String opsModel;   // launch | bau | launch+bau
    public String getOpsModel() { return opsModel; }
    public void setOpsModel(String v) { this.opsModel=v; }

    private String accountType;             // Strategic | Growth | Steady | Watch
    private BigDecimal revenueExposure;     // annual contract value (INR)
    private LocalDate contractEndDate;
    public String getAccountType() { return accountType; }
    public void setAccountType(String v) { this.accountType=v; }
    public BigDecimal getRevenueExposure() { return revenueExposure; }
    public void setRevenueExposure(BigDecimal v) { this.revenueExposure=v; }
    public LocalDate getContractEndDate() { return contractEndDate; }
    public void setContractEndDate(LocalDate v) { this.contractEndDate=v; }

    private Integer healthGreenThreshold;
    private Integer healthAmberThreshold;
    public Integer getHealthGreenThreshold() { return healthGreenThreshold; }
    public void setHealthGreenThreshold(Integer v) { this.healthGreenThreshold = v; }
    public Integer getHealthAmberThreshold() { return healthAmberThreshold; }
    public void setHealthAmberThreshold(Integer v) { this.healthAmberThreshold = v; }

    // Shared prod-bug pool marker (V80). When true, this Orbit project ingests bugs from
    // one Jira project that carries issues for many clients. `client_code_field` names the
    // Jira custom field (e.g. `customfield_11683`) that identifies the target client per issue.
    @Column(name = "is_shared_prod_bugs", nullable = false)
    private boolean sharedProdBugs = false;
    @Column(name = "client_code_field")
    private String clientCodeField;
    public boolean isSharedProdBugs() { return sharedProdBugs; }
    public void setSharedProdBugs(boolean v) { this.sharedProdBugs = v; }
    public String getClientCodeField() { return clientCodeField; }
    public void setClientCodeField(String v) { this.clientCodeField = v; }
}
