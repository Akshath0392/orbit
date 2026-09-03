package com.orbit.domain.alert;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "alert_notes")
public class AlertNote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @Column(name = "alert_id", nullable = false) private Long alertId;
    @Column(columnDefinition = "TEXT", nullable = false) private String note;
    private String createdBy;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getAlertId() { return alertId; }
    public void setAlertId(Long v) { this.alertId = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
