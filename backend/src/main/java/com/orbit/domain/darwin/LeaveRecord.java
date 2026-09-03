package com.orbit.domain.darwin;

import com.orbit.domain.client.AppUser;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_records")
public class LeaveRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private AppUser user;
    private String darwinEmpId;
    private String darwinLeaveId;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer workingDays;
    private String status;
    private String remarks;
    private LocalDateTime syncedAt = LocalDateTime.now();

    public LeaveRecord() {}
    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser v) { this.user = v; }
    public String getDarwinEmpId() { return darwinEmpId; }
    public void setDarwinEmpId(String v) { this.darwinEmpId = v; }
    public String getDarwinLeaveId() { return darwinLeaveId; }
    public void setDarwinLeaveId(String v) { this.darwinLeaveId = v; }
    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String v) { this.leaveType = v; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { this.endDate = v; }
    public Integer getWorkingDays() { return workingDays; }
    public void setWorkingDays(Integer v) { this.workingDays = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime v) { this.syncedAt = v; }
}
