package com.orbit.domain.account;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "project_releases")
public class ProjectRelease {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long projectId;
    private LocalDate releaseDate;
    private String releaseType;   // launch | bau | support
    private String label;
    private String rag;

    public ProjectRelease() {}
    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate v) { this.releaseDate = v; }
    public String getReleaseType() { return releaseType; }
    public void setReleaseType(String v) { this.releaseType = v; }
    public String getLabel() { return label; }
    public void setLabel(String v) { this.label = v; }
    public String getRag() { return rag; }
    public void setRag(String v) { this.rag = v; }
}
