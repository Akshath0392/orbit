package com.orbit.service.notification;

import com.orbit.domain.alert.Alert;
import com.orbit.domain.alert.NotificationEvent;
import com.orbit.domain.alert.PhaseStatus;
import com.orbit.integration.slack.SlackService;
import com.orbit.repository.AlertRepository;
import com.orbit.repository.NotificationEventRepository;
import com.orbit.repository.PhaseStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class NotificationSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchedulerService.class);

    private final PhaseStatusRepository phaseStatusRepo;
    private final AlertRepository alertRepo;
    private final NotificationEventRepository eventRepo;
    private final SlackService slackService;

    public NotificationSchedulerService(PhaseStatusRepository phaseStatusRepo,
                                        AlertRepository alertRepo,
                                        NotificationEventRepository eventRepo,
                                        SlackService slackService) {
        this.phaseStatusRepo = phaseStatusRepo;
        this.alertRepo = alertRepo;
        this.eventRepo = eventRepo;
        this.slackService = slackService;
    }

    /** Runs every 30 minutes. Evaluates T-2, T-1, D-Day, and overdue for all active phases. */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    @Transactional
    public void evaluate() {
        LocalDate today = LocalDate.now();
        log.info("NotificationScheduler: evaluating phase statuses for {}", today);

        List<PhaseStatus> active = phaseStatusRepo.findAllActive();

        for (PhaseStatus ps : active) {
            if (ps.getEndDate() == null) continue;

            long daysUntil = ChronoUnit.DAYS.between(today, ps.getEndDate());

            if (daysUntil == 2 && !today.equals(ps.getLastNotifiedT2())) {
                sendReminder(ps, "T2_REMINDER",
                    buildPreDueMessage(ps, 2));
                ps.setLastNotifiedT2(today);
                phaseStatusRepo.save(ps);

            } else if (daysUntil == 1 && !today.equals(ps.getLastNotifiedT1())) {
                sendReminder(ps, "T1_REMINDER",
                    buildPreDueMessage(ps, 1));
                ps.setLastNotifiedT1(today);
                phaseStatusRepo.save(ps);

            } else if (daysUntil == 0 && !Boolean.TRUE.equals(ps.getDdayNotified())) {
                sendReminder(ps, "DDAY_PROMPT",
                    buildDdayMessage(ps));
                ps.setDdayNotified(true);
                phaseStatusRepo.save(ps);

            } else if (daysUntil < 0) {
                flagOverdue(ps, today);
            }
        }
    }

    private void flagOverdue(PhaseStatus ps, LocalDate today) {
        long daysLate = ChronoUnit.DAYS.between(ps.getEndDate(), today);

        if (!"COMPLETED".equals(ps.getStatus()) && !"DELAYED_SYSTEM".equals(ps.getStatus())) {
            ps.setStatus("DELAYED_SYSTEM");
            ps.setUpdatedAt(LocalDateTime.now());
            phaseStatusRepo.save(ps);
            createOverdueAlert(ps, (int) daysLate);
            sendEscalation(ps, (int) daysLate);
        }
    }

    private void createOverdueAlert(PhaseStatus ps, int daysLate) {
        String projectName = ps.getProject() != null ? ps.getProject().getName() : "Unknown";
        Alert alert = new Alert();
        alert.setAlertType("PHASE_OVERDUE");
        alert.setSeverity(daysLate >= 3 ? "critical" : "risk");
        alert.setTitle(ps.getPhase() + " overdue – " + projectName);
        alert.setDetail(String.format("%s phase for project %s is %d day(s) overdue. Assignee: %s",
            ps.getPhase(), projectName, daysLate, orUnknown(ps.getAssigneeName())));
        alert.setPhase(ps.getPhase());
        alert.setDaysOverdue(daysLate);
        alert.setSourceAgent("notification-scheduler");
        if (ps.getProject() != null) {
            alert.setProject(ps.getProject());
            alert.setClient(ps.getProject().getClient());
        }
        alertRepo.save(alert);
        log.info("Created overdue alert for {} phase of project {}", ps.getPhase(), projectName);
    }

    private void sendReminder(PhaseStatus ps, String eventType, String message) {
        if (ps.getAssigneeEmail() == null) {
            log.debug("No assignee email for {} phase of project {}", ps.getPhase(),
                ps.getProject() != null ? ps.getProject().getName() : "?");
            return;
        }

        // Check dedup: don't re-send same event type within 23 hours
        boolean alreadySent = eventRepo.existsByPhaseStatusIdAndEventTypeAndSentAtAfter(
            ps.getId(), eventType, LocalDateTime.now().minusHours(23));
        if (alreadySent) return;

        String slackUserId = slackService.resolveSlackUserId(ps.getAssigneeEmail())
            .orElse(null);

        if (slackUserId != null) {
            slackService.sendDm(slackUserId, message);
        } else {
            log.warn("Could not resolve Slack user for {}", ps.getAssigneeEmail());
        }

        NotificationEvent event = new NotificationEvent();
        event.setPhaseStatus(ps);
        event.setProject(ps.getProject());
        event.setPhase(ps.getPhase());
        event.setEventType(eventType);
        event.setRecipientEmail(ps.getAssigneeEmail());
        event.setRecipientName(ps.getAssigneeName());
        eventRepo.save(event);
    }

    private void sendEscalation(PhaseStatus ps, int daysLate) {
        String projectName = ps.getProject() != null ? ps.getProject().getName() : "Unknown";
        String msg = String.format(
            "ESCALATION — %s Overdue | %s%n" +
            "Assignee: %s | Phase: %s | Due Date: %s | Days Overdue: %d%n" +
            "Note: %s",
            ps.getPhase(), projectName,
            orUnknown(ps.getAssigneeName()), ps.getPhase(), ps.getEndDate(), daysLate,
            ps.getDelayNote() != null ? ps.getDelayNote() : "—");

        // Send to project channel if configured
        if (ps.getProject() != null) {
            slackService.resolveChannel(ps.getProject().getId())
                .ifPresent(ch -> slackService.sendToChannel(ch, msg));
        }

        NotificationEvent event = new NotificationEvent();
        event.setPhaseStatus(ps);
        event.setProject(ps.getProject());
        event.setPhase(ps.getPhase());
        event.setEventType("ESCALATION");
        eventRepo.save(event);
    }

    private String buildPreDueMessage(PhaseStatus ps, int daysAway) {
        String projectName = ps.getProject() != null ? ps.getProject().getName() : "Unknown";
        return String.format(
            "DEADLINE REMINDER — %s | %s%n" +
            "Hi %s, your %s deadline for %s is in %d day(s).%n" +
            "Due Date: %s. Please ensure you are on track.",
            ps.getPhase(), projectName,
            orUnknown(ps.getAssigneeName()), ps.getPhase(), projectName, daysAway,
            ps.getEndDate());
    }

    private String buildDdayMessage(PhaseStatus ps) {
        String projectName = ps.getProject() != null ? ps.getProject().getName() : "Unknown";
        return String.format(
            "D-DAY CHECK-IN — %s | %s%n" +
            "Hi %s, today is your %s deadline (%s).%n" +
            "Please update your status in the Orbit dashboard: On Track or Delayed?",
            ps.getPhase(), projectName,
            orUnknown(ps.getAssigneeName()), ps.getPhase(), ps.getEndDate());
    }

    private String orUnknown(String v) {
        return v != null ? v : "Unknown";
    }
}
