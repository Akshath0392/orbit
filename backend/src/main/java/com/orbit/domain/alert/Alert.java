package com.orbit.domain.alert;

import com.orbit.domain.client.*;
import com.orbit.domain.issue.JiraIssue;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="alerts")
public class Alert {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String alertType;
    private String severity;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="issue_id") private JiraIssue issue;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="client_id") private Client client;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id") private Project project;
    private String title;
    @Column(columnDefinition="TEXT") private String detail;
    private String sourceAgent;
    private String status = "OPEN";
    private String ownerName;
    private LocalDate followUpDate;
    @Column(columnDefinition="TEXT") private String mitigationNote;
    @Column(columnDefinition="TEXT") private String aiExplanation;
    private String phase;
    private Integer daysOverdue;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime resolvedAt;

    public Alert() {}
    public Long getId() { return id; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String v) { this.alertType=v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity=v; }
    public JiraIssue getIssue() { return issue; }
    public void setIssue(JiraIssue v) { this.issue=v; }
    public Client getClient() { return client; }
    public void setClient(Client v) { this.client=v; }
    public Project getProject() { return project; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title=v; }
    public String getDetail() { return detail; }
    public void setDetail(String v) { this.detail=v; }
    public String getSourceAgent() { return sourceAgent; }
    public void setSourceAgent(String v) { this.sourceAgent=v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status=v; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName=v; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public void setFollowUpDate(LocalDate v) { this.followUpDate=v; }
    public String getMitigationNote() { return mitigationNote; }
    public void setMitigationNote(String v) { this.mitigationNote=v; }
    public String getPhase() { return phase; }
    public void setPhase(String v) { this.phase=v; }
    public Integer getDaysOverdue() { return daysOverdue; }
    public void setDaysOverdue(Integer v) { this.daysOverdue=v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setProject(Project v) { this.project=v; }
}
