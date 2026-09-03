package com.orbit.service;

import com.orbit.domain.client.Client;
import com.orbit.domain.client.SlaRule;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.SlaRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SlaService {

    private static final Logger log = LoggerFactory.getLogger(SlaService.class);
    private static final List<String> CLOSED = List.of("Closed","Invalid","Resolved","Canceled");

    private final SlaRuleRepository rules;
    private final JiraIssueRepository issues;

    public SlaService(SlaRuleRepository rules, JiraIssueRepository issues) {
        this.rules = rules; this.issues = issues;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Resolve the applicable rule: client override → default → null. */
    public Optional<SlaRule> resolveRule(String severity, Client client) {
        if (severity == null) return Optional.empty();
        if (client != null) {
            Optional<SlaRule> override = rules.findBySeverityAndClientId(severity, client.getId());
            if (override.isPresent()) return override;
        }
        return rules.findBySeverityAndClientIsNull(severity);
    }

    /** Compute elapsed hours, respecting business-hours flag. */
    public double elapsedHours(LocalDateTime createdAt, boolean includeWeekends) {
        if (createdAt == null) return 0;
        LocalDateTime now = LocalDateTime.now();
        if (includeWeekends) {
            return Duration.between(createdAt, now).toMinutes() / 60.0;
        }
        return businessHours(createdAt, now);
    }

    /**
     * Compute SLA status for a bug.
     * - elapsed >= resolutionHours → "Breached"
     * - elapsed >= responseHours  → "At risk"
     * - otherwise                 → "On track"
     */
    public String computeStatus(String severity, LocalDateTime createdAt, Client client) {
        return resolveRule(severity, client).map(rule -> {
            double elapsed = elapsedHours(createdAt, Boolean.TRUE.equals(rule.getIncludeWeekends()));
            if (elapsed >= rule.getResolutionHours().doubleValue()) return "Breached";
            if (elapsed >= rule.getResponseHours().doubleValue())   return "At risk";
            return "On track";
        }).orElse("On track");
    }

    /** Remaining hours until breach (negative = already breached). */
    public BigDecimal computeRemainingHours(String severity, LocalDateTime createdAt, Client client) {
        return resolveRule(severity, client).map(rule -> {
            double elapsed  = elapsedHours(createdAt, Boolean.TRUE.equals(rule.getIncludeWeekends()));
            double remaining = rule.getResolutionHours().doubleValue() - elapsed;
            return BigDecimal.valueOf(Math.round(remaining * 10.0) / 10.0);
        }).orElse(null);
    }

    /** Apply SLA computation to a single issue and return whether status changed. */
    public boolean applySla(JiraIssue issue) {
        if (!"PROD_BUG".equals(issue.getIssueType()) && !"UAT_BUG".equals(issue.getIssueType())) return false;
        String prev = issue.getSlaStatus();
        String next = computeStatus(issue.getSeverity(), issue.getCreatedAt(), issue.getClient());
        BigDecimal rem = computeRemainingHours(issue.getSeverity(), issue.getCreatedAt(), issue.getClient());
        issue.setSlaStatus(next);
        issue.setSlaRemainingHours(rem);
        return !next.equals(prev);
    }

    // ── Jira SLA field parsing (JSM customfield) ───────────────────────────────

    /**
     * Try to read SLA from a Jira JSM custom field value.
     * Returns null if the field is absent or unparseable — caller falls back to computed.
     *
     * JSM SLA field shape:
     * { "ongoingCycle": { "remainingTime": { "millis": 3600000 }, "breached": false } }
     */
    @SuppressWarnings("unchecked")
    public String parseJiraSlaStatus(Object fieldValue) {
        if (!(fieldValue instanceof java.util.Map)) return null;
        try {
            java.util.Map<String,Object> sla = (java.util.Map<String,Object>) fieldValue;
            java.util.Map<String,Object> cycle = (java.util.Map<String,Object>) sla.get("ongoingCycle");
            if (cycle == null) {
                // Check completedCycles for already-breached
                java.util.List<?> completed = (java.util.List<?>) sla.get("completedCycles");
                if (completed != null && !completed.isEmpty()) {
                    java.util.Map<String,Object> last = (java.util.Map<String,Object>) completed.get(completed.size() - 1);
                    return Boolean.TRUE.equals(last.get("breached")) ? "Breached" : "On track";
                }
                return null;
            }
            if (Boolean.TRUE.equals(cycle.get("breached"))) return "Breached";
            java.util.Map<String,Object> remaining = (java.util.Map<String,Object>) cycle.get("remainingTime");
            if (remaining == null) return "On track";
            long millis = ((Number) remaining.get("millis")).longValue();
            if (millis <= 0) return "Breached";
            // At risk: less than 20% remaining of resolution hours (approximation)
            return millis < 3_600_000L ? "At risk" : "On track"; // < 1 hour remaining → at risk
        } catch (Exception e) {
            return null; // unparseable — fall back to computed
        }
    }

    @SuppressWarnings("unchecked")
    public BigDecimal parseJiraSlaRemaining(Object fieldValue) {
        if (!(fieldValue instanceof java.util.Map)) return null;
        try {
            java.util.Map<String,Object> sla = (java.util.Map<String,Object>) fieldValue;
            java.util.Map<String,Object> cycle = (java.util.Map<String,Object>) sla.get("ongoingCycle");
            if (cycle == null) return null;
            java.util.Map<String,Object> remaining = (java.util.Map<String,Object>) cycle.get("remainingTime");
            if (remaining == null) return null;
            long millis = ((Number) remaining.get("millis")).longValue();
            return BigDecimal.valueOf(Math.round(millis / 360_000.0) / 10.0); // millis → hours, 1dp
        } catch (Exception e) {
            return null;
        }
    }

    // ── Scheduled recompute ────────────────────────────────────────────────────

    /** Runs every hour to refresh slaStatus + slaRemainingHours for all open bugs. */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void recomputeAll() {
        log.info("SlaService: recomputing SLA for all open bugs");
        int updated = 0;
        for (JiraIssue issue : issues.findAll()) {
            if (CLOSED.contains(issue.getLifecycleStage())) continue;
            if (!"PROD_BUG".equals(issue.getIssueType()) && !"UAT_BUG".equals(issue.getIssueType())) continue;
            if (applySla(issue)) { issues.save(issue); updated++; }
        }
        log.info("SlaService: updated SLA status for {} issues", updated);
    }

    // ── Business hours helper ──────────────────────────────────────────────────

    // 9am–6pm Mon–Fri (9 business hours/day)
    private static final int BIZ_START = 9;
    private static final int BIZ_END   = 18;

    private static double businessHours(LocalDateTime from, LocalDateTime to) {
        if (!from.isBefore(to)) return 0;
        double total = 0;
        LocalDateTime cursor = from;
        while (cursor.toLocalDate().isBefore(to.toLocalDate())) {
            total += bizHoursInDay(cursor, cursor.toLocalDate().atTime(23, 59, 59));
            cursor = cursor.toLocalDate().plusDays(1).atStartOfDay();
        }
        total += bizHoursInDay(cursor, to);
        return total;
    }

    private static double bizHoursInDay(LocalDateTime from, LocalDateTime to) {
        DayOfWeek day = from.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return 0;
        LocalDateTime bizStart = from.toLocalDate().atTime(BIZ_START, 0);
        LocalDateTime bizEnd   = from.toLocalDate().atTime(BIZ_END, 0);
        LocalDateTime effectiveFrom = from.isBefore(bizStart) ? bizStart : from;
        LocalDateTime effectiveTo   = to.isAfter(bizEnd)      ? bizEnd   : to;
        if (!effectiveFrom.isBefore(effectiveTo)) return 0;
        return Duration.between(effectiveFrom, effectiveTo).toMinutes() / 60.0;
    }
}
