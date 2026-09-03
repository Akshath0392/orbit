package com.orbit.domain.account;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "governance_meetings")
public class GovernanceMeeting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long projectId;
    private Long portfolioId;
    @Column(nullable = false) private String cadence;
    @Column(nullable = false) private String title;
    private LocalDate lastHeld;
    private LocalDate nextDue;
    private String owner;
    private String status;
    private String notes;

    public GovernanceMeeting() {}
    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }
    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long v) { this.portfolioId = v; }
    public String getCadence() { return cadence; }
    public void setCadence(String v) { this.cadence = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public LocalDate getLastHeld() { return lastHeld; }
    public void setLastHeld(LocalDate v) { this.lastHeld = v; }
    public LocalDate getNextDue() { return nextDue; }
    public void setNextDue(LocalDate v) { this.nextDue = v; }
    public String getOwner() { return owner; }
    public void setOwner(String v) { this.owner = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
}
