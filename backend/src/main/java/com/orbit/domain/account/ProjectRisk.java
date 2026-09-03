package com.orbit.domain.account;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_risks")
public class ProjectRisk {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long projectId;
    private String jiraTicket;
    @Column(nullable = false) private String risk;
    private LocalDate receivedOn;
    private String rag;            // Green | Amber | Red
    private LocalDate actionEnd;
    private String actionOwner;
    private String source;
    private LocalDateTime createdAt = LocalDateTime.now();
    private String createdBy;

    public ProjectRisk() {}
    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }
    public String getJiraTicket() { return jiraTicket; }
    public void setJiraTicket(String v) { this.jiraTicket = v; }
    public String getRisk() { return risk; }
    public void setRisk(String v) { this.risk = v; }
    public LocalDate getReceivedOn() { return receivedOn; }
    public void setReceivedOn(LocalDate v) { this.receivedOn = v; }
    public String getRag() { return rag; }
    public void setRag(String v) { this.rag = v; }
    public LocalDate getActionEnd() { return actionEnd; }
    public void setActionEnd(LocalDate v) { this.actionEnd = v; }
    public String getActionOwner() { return actionOwner; }
    public void setActionOwner(String v) { this.actionOwner = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
}
