package com.orbit.domain.account;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_team")
public class ProjectTeam {
    @Id @Column(name = "project_id") private Long projectId;
    private String internalPm;
    private String internalAm;
    private String internalEm;
    private String internalSol;
    private String internalTechLead;
    private String internalQaLead;
    private String internalSupportMgr;
    private String clientSponsor;
    private String clientTechSpoc;
    private String clientBizSpoc;
    private String clientPm;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public ProjectTeam() {}
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }
    public String getInternalPm() { return internalPm; }
    public void setInternalPm(String v) { this.internalPm = v; }
    public String getInternalAm() { return internalAm; }
    public void setInternalAm(String v) { this.internalAm = v; }
    public String getInternalEm() { return internalEm; }
    public void setInternalEm(String v) { this.internalEm = v; }
    public String getInternalSol() { return internalSol; }
    public void setInternalSol(String v) { this.internalSol = v; }
    public String getInternalTechLead() { return internalTechLead; }
    public void setInternalTechLead(String v) { this.internalTechLead = v; }
    public String getInternalQaLead() { return internalQaLead; }
    public void setInternalQaLead(String v) { this.internalQaLead = v; }
    public String getInternalSupportMgr() { return internalSupportMgr; }
    public void setInternalSupportMgr(String v) { this.internalSupportMgr = v; }
    public String getClientSponsor() { return clientSponsor; }
    public void setClientSponsor(String v) { this.clientSponsor = v; }
    public String getClientTechSpoc() { return clientTechSpoc; }
    public void setClientTechSpoc(String v) { this.clientTechSpoc = v; }
    public String getClientBizSpoc() { return clientBizSpoc; }
    public void setClientBizSpoc(String v) { this.clientBizSpoc = v; }
    public String getClientPm() { return clientPm; }
    public void setClientPm(String v) { this.clientPm = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
}
