package com.orbit.domain.darwin;

import com.orbit.domain.client.AppUser;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_balances")
public class LeaveBalance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private AppUser user;
    private String darwinEmpId;
    private String leaveType;
    private BigDecimal totalDays = BigDecimal.ZERO;
    private BigDecimal takenDays = BigDecimal.ZERO;
    private BigDecimal pendingDays = BigDecimal.ZERO;
    private BigDecimal remainingDays = BigDecimal.ZERO;
    private LocalDateTime syncedAt = LocalDateTime.now();

    public LeaveBalance() {}
    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser v) { this.user = v; }
    public String getDarwinEmpId() { return darwinEmpId; }
    public void setDarwinEmpId(String v) { this.darwinEmpId = v; }
    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String v) { this.leaveType = v; }
    public BigDecimal getTotalDays() { return totalDays; }
    public void setTotalDays(BigDecimal v) { this.totalDays = v; }
    public BigDecimal getTakenDays() { return takenDays; }
    public void setTakenDays(BigDecimal v) { this.takenDays = v; }
    public BigDecimal getPendingDays() { return pendingDays; }
    public void setPendingDays(BigDecimal v) { this.pendingDays = v; }
    public BigDecimal getRemainingDays() { return remainingDays; }
    public void setRemainingDays(BigDecimal v) { this.remainingDays = v; }
    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime v) { this.syncedAt = v; }
}
