package com.orbit.domain.darwin;

import com.orbit.domain.client.AppUser;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private AppUser user;
    private String darwinEmpId;
    private LocalDate attendanceDate;
    private LocalTime checkIn;
    private LocalTime checkOut;
    private BigDecimal workingHours;
    private String status;   // Present, Absent, Late, Half-day, WFH, Holiday
    private LocalDateTime syncedAt = LocalDateTime.now();

    public AttendanceRecord() {}
    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser v) { this.user = v; }
    public String getDarwinEmpId() { return darwinEmpId; }
    public void setDarwinEmpId(String v) { this.darwinEmpId = v; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public void setAttendanceDate(LocalDate v) { this.attendanceDate = v; }
    public LocalTime getCheckIn() { return checkIn; }
    public void setCheckIn(LocalTime v) { this.checkIn = v; }
    public LocalTime getCheckOut() { return checkOut; }
    public void setCheckOut(LocalTime v) { this.checkOut = v; }
    public BigDecimal getWorkingHours() { return workingHours; }
    public void setWorkingHours(BigDecimal v) { this.workingHours = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime v) { this.syncedAt = v; }
}
