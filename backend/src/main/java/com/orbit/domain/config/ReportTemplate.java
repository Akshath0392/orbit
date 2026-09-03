package com.orbit.domain.config;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "export_templates")
public class ReportTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String name;
    private String scope = "acct";
    @Column(columnDefinition = "text") private String sections;
    @Column(name = "is_default") private boolean defaultTemplate;
    private String updatedBy;
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getScope() { return scope; }
    public void setScope(String v) { this.scope = v; }
    public String getSections() { return sections; }
    public void setSections(String v) { this.sections = v; }
    public boolean isDefaultTemplate() { return defaultTemplate; }
    public void setDefaultTemplate(boolean v) { this.defaultTemplate = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
