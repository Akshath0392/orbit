package com.orbit.domain.darwin;

import com.orbit.domain.client.AppUser;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "wfh_records")
public class WfhRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private AppUser user;
    private String darwinEmpId;
    private String darwinWfhId;
    private LocalDate wfhDate;
    private String wfhType = "FULL_DAY";
    private String status;
    private String reason;
    private LocalDateTime syncedAt = LocalDateTime.now();

    public WfhRecord() {}
    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser v) { this.user = v; }
    public String getDarwinEmpId() { return darwinEmpId; }
    public void setDarwinEmpId(String v) { this.darwinEmpId = v; }
    public String getDarwinWfhId() { return darwinWfhId; }
    public void setDarwinWfhId(String v) { this.darwinWfhId = v; }
    public LocalDate getWfhDate() { return wfhDate; }
    public void setWfhDate(LocalDate v) { this.wfhDate = v; }
    public String getWfhType() { return wfhType; }
    public void setWfhType(String v) { this.wfhType = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime v) { this.syncedAt = v; }
}
