package com.orbit.domain.report;

import com.orbit.domain.client.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="generated_reports")
public class GeneratedReport {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String type;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="client_id") private Client client;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id") private Project project;
    private String status = "GENERATING";
    private String generatedBy;
    @Column(columnDefinition="TEXT") private String manualNotes;
    private Boolean clientSafe = true;
    @Column(name="content_json") private String contentJson;
    private LocalDateTime generatedAt = LocalDateTime.now();

    public GeneratedReport() {}
    public Long getId() { return id; }
    public String getType() { return type; }
    public void setType(String v) { this.type=v; }
    public Client getClient() { return client; }
    public void setClient(Client v) { this.client=v; }
    public Project getProject() { return project; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status=v; }
    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String v) { this.generatedBy=v; }
    public String getManualNotes() { return manualNotes; }
    public void setManualNotes(String v) { this.manualNotes=v; }
    public Boolean getClientSafe() { return clientSafe; }
    public void setClientSafe(Boolean v) { this.clientSafe=v; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String v) { this.contentJson=v; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private GeneratedReport r = new GeneratedReport();
        public Builder type(String v) { r.type=v; return this; }
        public Builder client(Client v) { r.client=v; return this; }
        public Builder project(Project v) { r.project=v; return this; }
        public Builder status(String v) { r.status=v; return this; }
        public Builder generatedBy(String v) { r.generatedBy=v; return this; }
        public Builder clientSafe(Boolean v) { r.clientSafe=v; return this; }
        public GeneratedReport build() { return r; }
    }
}
