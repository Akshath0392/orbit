package com.orbit.domain.issue;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="issue_notes")
public class IssueNote {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="issue_id") private JiraIssue issue;
    @Column(columnDefinition="TEXT") private String text;
    private Boolean isClientSafe = false;
    private String createdBy;
    private LocalDateTime createdAt = LocalDateTime.now();

    public IssueNote() {}
    public Long getId() { return id; }
    public JiraIssue getIssue() { return issue; }
    public void setIssue(JiraIssue v) { this.issue=v; }
    public String getText() { return text; }
    public void setText(String v) { this.text=v; }
    public Boolean getIsClientSafe() { return isClientSafe; }
    public void setIsClientSafe(Boolean v) { this.isClientSafe=v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy=v; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private IssueNote n = new IssueNote();
        public Builder issue(JiraIssue v) { n.issue=v; return this; }
        public Builder text(String v) { n.text=v; return this; }
        public Builder isClientSafe(Boolean v) { n.isClientSafe=v; return this; }
        public Builder createdBy(String v) { n.createdBy=v; return this; }
        public IssueNote build() { return n; }
    }
}
