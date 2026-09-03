package com.orbit.domain.alert;

import com.orbit.domain.client.Project;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_events")
public class NotificationEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private NotificationRule rule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_status_id")
    private PhaseStatus phaseStatus;

    private String phase;
    private String eventType;
    private String recipientEmail;
    private String recipientName;
    private String slackMsgTs;
    private String userResponse;
    private LocalDateTime respondedAt;
    private LocalDateTime sentAt = LocalDateTime.now();

    public NotificationEvent() {}

    public Long getId() { return id; }
    public NotificationRule getRule() { return rule; }
    public void setRule(NotificationRule v) { this.rule = v; }
    public Project getProject() { return project; }
    public void setProject(Project v) { this.project = v; }
    public PhaseStatus getPhaseStatus() { return phaseStatus; }
    public void setPhaseStatus(PhaseStatus v) { this.phaseStatus = v; }
    public String getPhase() { return phase; }
    public void setPhase(String v) { this.phase = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String v) { this.recipientEmail = v; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String v) { this.recipientName = v; }
    public String getSlackMsgTs() { return slackMsgTs; }
    public void setSlackMsgTs(String v) { this.slackMsgTs = v; }
    public String getUserResponse() { return userResponse; }
    public void setUserResponse(String v) { this.userResponse = v; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime v) { this.respondedAt = v; }
    public LocalDateTime getSentAt() { return sentAt; }
}
