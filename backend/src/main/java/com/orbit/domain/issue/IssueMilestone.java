package com.orbit.domain.issue;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name="issue_milestones")
public class IssueMilestone {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="issue_id") private JiraIssue issue;
    private String milestoneType;
    private LocalDate targetDate;
    private LocalDate actualDate;
    private Boolean isTbc = false;
    private String status;
    private String source = "JIRA_FIELD";

    public IssueMilestone() {}
    public Long getId() { return id; }
    public JiraIssue getIssue() { return issue; }
    public void setIssue(JiraIssue v) { this.issue=v; }
    public String getMilestoneType() { return milestoneType; }
    public void setMilestoneType(String v) { this.milestoneType=v; }
    public LocalDate getTargetDate() { return targetDate; }
    public Boolean getIsTbc() { return isTbc; }
    public void setIsTbc(Boolean v) { this.isTbc=v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status=v; }
}
