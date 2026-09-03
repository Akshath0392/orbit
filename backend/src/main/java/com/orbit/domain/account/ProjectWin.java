package com.orbit.domain.account;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_wins")
public class ProjectWin {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long projectId;
    @Column(nullable = false) private String win;
    private LocalDate recognisedOn;
    private String source;
    private LocalDateTime createdAt = LocalDateTime.now();
    private String createdBy;

    public ProjectWin() {}
    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }
    public String getWin() { return win; }
    public void setWin(String v) { this.win = v; }
    public LocalDate getRecognisedOn() { return recognisedOn; }
    public void setRecognisedOn(LocalDate v) { this.recognisedOn = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
}
