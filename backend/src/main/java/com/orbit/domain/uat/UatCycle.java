package com.orbit.domain.uat;

import com.orbit.domain.issue.JiraIssue;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="uat_cycles")
public class UatCycle {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="issue_id") private JiraIssue issue;
    private Integer cycleNumber;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String signOffStatus = "PENDING";
    private String signedOffBy;
    private LocalDateTime signedOffAt;
    private String envSnapshot;
    @Column(columnDefinition="TEXT") private String notes;

    public UatCycle() {}
    public Long getId() { return id; }
    public JiraIssue getIssue() { return issue; }
    public void setIssue(JiraIssue v) { this.issue=v; }
    public Integer getCycleNumber() { return cycleNumber; }
    public void setCycleNumber(Integer v) { this.cycleNumber=v; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime v) { this.startedAt=v; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public String getSignOffStatus() { return signOffStatus; }
    public void setSignOffStatus(String v) { this.signOffStatus=v; }
    public String getSignedOffBy() { return signedOffBy; }
    public void setSignedOffBy(String v) { this.signedOffBy=v; }
    public LocalDateTime getSignedOffAt() { return signedOffAt; }
    public void setSignedOffAt(LocalDateTime v) { this.signedOffAt=v; }
    public String getEnvSnapshot() { return envSnapshot; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes=v; }
}
