package com.orbit.service.sync;

import com.orbit.domain.issue.JiraIssue;
import com.orbit.domain.issue.Sprint;
import com.orbit.domain.issue.SprintIssue;
import com.orbit.repository.SprintIssueRepository;
import com.orbit.repository.SprintRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Read-side sprint metrics (F3 §metrics): committed vs delivered story points,
 * spillover, scope change, commitment reliability. Keeps the math out of the
 * controllers. Definitions:
 *  - committed  = snapshot at activation (committed_story_points); pre-rollout
 *                 sprints approximate from membership + current SP → approx=true
 *  - delivered  = SP of issues resolved inside the sprint window while member
 *  - spillover% = committed-not-delivered SP ÷ committed SP
 *  - scope+%    = SP added after start(+grace) ÷ committed SP
 * Every payload carries unpointedCount — unpointed issues are visible, never
 * silently counted as 0.
 */
@Service
public class VelocityService {

    private static final long GRACE_MINUTES = 15;

    private final SprintRepository sprints;
    private final SprintIssueRepository memberships;

    public VelocityService(SprintRepository sprints, SprintIssueRepository memberships) {
        this.sprints = sprints;
        this.memberships = memberships;
    }

    /** Per-sprint aggregate for one scope (portfolio or client). */
    public record SprintStats(Sprint sprint, double committed, double delivered, double addedAfterStart,
                              double committedDelivered, int unpointedCount, boolean approx, int members,
                              double devSp, double uatSp, double prodSp) {
        public Double pct() { return committed == 0 ? null : Math.round(1000.0 * delivered / committed) / 10.0; }
    }

    public List<SprintStats> recentSprints(Long portfolioId, Long clientId, int n) {
        List<Sprint> recent = clientId != null
            ? sprints.findRecentForClient(clientId, PageRequest.of(0, n))
            : sprints.findRecentForPortfolio(portfolioId, PageRequest.of(0, n));
        List<SprintStats> out = new ArrayList<>();
        for (Sprint sprint : recent) {
            out.add(compute(sprint, portfolioId, clientId));
        }
        out.sort(Comparator.comparing(s -> s.sprint().getStartDate(),
            Comparator.nullsFirst(Comparator.naturalOrder())));
        return out;
    }

    private SprintStats compute(Sprint sprint, Long portfolioId, Long clientId) {
        boolean snapshot = sprint.getCommittedSnapshotAt() != null;
        LocalDateTime start = sprint.getStartDate();
        LocalDateTime cutoff = start == null ? null : start.plusMinutes(GRACE_MINUTES);
        LocalDateTime windowEnd = sprint.getCompleteDate() != null ? sprint.getCompleteDate()
            : sprint.getEndDate() != null ? sprint.getEndDate() : LocalDateTime.now();

        double committed = 0, delivered = 0, committedDelivered = 0, added = 0;
        double devSp = 0, uatSp = 0, prodSp = 0;
        int unpointed = 0, members = 0;

        for (Object[] row : memberships.findVelocityRows(sprint.getId(), portfolioId, clientId)) {
            SprintIssue si = (SprintIssue) row[0];
            JiraIssue issue = (JiraIssue) row[1];
            members++;
            double sp = issue.getStoryPoints() == null ? 0 : issue.getStoryPoints().doubleValue();
            if (issue.getStoryPoints() == null) unpointed++;

            boolean isCommitted = snapshot
                ? Boolean.TRUE.equals(si.getCommitted())
                : si.getRemovedAt() == null && (si.getAddedAt() == null || cutoff == null || !si.getAddedAt().isAfter(cutoff));
            double committedSp = snapshot && si.getCommittedStoryPoints() != null
                ? si.getCommittedStoryPoints().doubleValue() : sp;
            if (isCommitted) committed += committedSp;
            else if (si.getAddedAt() != null && cutoff != null && si.getAddedAt().isAfter(cutoff)) added += sp;

            boolean resolvedInWindow = issue.getResolvedAt() != null
                && (start == null || !issue.getResolvedAt().isBefore(start))
                && !issue.getResolvedAt().isAfter(windowEnd)
                && (si.getRemovedAt() == null || si.getRemovedAt().isAfter(issue.getResolvedAt()));
            if (resolvedInWindow) {
                delivered += sp;
                if (isCommitted) committedDelivered += committedSp;
            } else if ("active".equals(sprint.getState()) && si.getRemovedAt() == null) {
                // live sprint breakdown by current stage bucket
                String stage = issue.getLifecycleStage() == null ? "" : issue.getLifecycleStage().toLowerCase();
                if (stage.contains("uat") || stage.contains("customer validation")) uatSp += sp;
                else if (stage.contains("prod") || stage.equals("fixed") || stage.equals("released")) prodSp += sp;
                else devSp += sp;
            }
        }
        return new SprintStats(sprint, committed, delivered, added, committedDelivered,
            unpointed, !snapshot, members, devSp, uatSp, prodSp);
    }

    /** W5 payload — last n sprints, oldest→newest, plus the live breakdown. */
    public Map<String, Object> velocityPayload(Long portfolioId, Long clientId, int n) {
        List<SprintStats> stats = recentSprints(portfolioId, clientId, n);
        List<Map<String, Object>> sprintRows = new ArrayList<>();
        for (SprintStats s : stats) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sprintId", s.sprint().getJiraSprintId());
            m.put("label", s.sprint().getName());
            m.put("state", s.sprint().getState());
            m.put("live", "active".equals(s.sprint().getState()));
            m.put("startDate", s.sprint().getStartDate());
            m.put("endDate", s.sprint().getEndDate());
            m.put("committed", round1(s.committed()));
            m.put("delivered", round1(s.delivered()));
            m.put("pct", s.pct());
            m.put("unpointedCount", s.unpointedCount());
            m.put("approx", s.approx());
            if ("active".equals(s.sprint().getState())) {
                m.put("breakdown", Map.of(
                    "dev", round1(s.devSp()), "uat", round1(s.uatSp()), "prod", round1(s.prodSp())));
            }
            sprintRows.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dataAvailable", !sprintRows.isEmpty());
        out.put("sprints", sprintRows);

        // sprint-on-sprint: last two CLOSED sprints WITH pointed work (mock
        // velSoS) — sprints where nothing was pointed carry no signal and used
        // to blank the POD card
        List<SprintStats> closed = stats.stream()
            .filter(s -> "closed".equals(s.sprint().getState()))
            .filter(s -> s.committed() > 0).toList();
        if (closed.size() >= 1) {
            SprintStats last = closed.get(closed.size() - 1);
            Double lastPct = last.pct();
            Double prevPct = closed.size() >= 2 ? closed.get(closed.size() - 2).pct() : null;
            Map<String, Object> sos = new LinkedHashMap<>();
            sos.put("pct", lastPct);
            sos.put("delta", lastPct == null || prevPct == null ? null : Math.round((lastPct - prevPct) * 10) / 10.0);
            sos.put("approx", last.approx());
            sos.put("sprint", last.sprint().getName());
            out.put("velocitySoS", sos);
        }
        return out;
    }

    /**
     * Predictability inputs over the last n CLOSED sprints (dh-metrics pred
     * pillar): commitment reliability %, spillover %, scope change %.
     */
    public Map<String, Object> predictability(Long clientId, int n) {
        List<SprintStats> closed = recentSprints(null, clientId, n + 2).stream()
            .filter(s -> "closed".equals(s.sprint().getState()))
            .toList();
        if (closed.isEmpty()) return Map.of("dataAvailable", false);
        double committed = closed.stream().mapToDouble(SprintStats::committed).sum();
        double committedDelivered = closed.stream().mapToDouble(SprintStats::committedDelivered).sum();
        double added = closed.stream().mapToDouble(SprintStats::addedAfterStart).sum();
        boolean approx = closed.stream().anyMatch(SprintStats::approx);
        if (committed == 0) return Map.of("dataAvailable", false);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dataAvailable", true);
        out.put("sprints", closed.size());
        out.put("commitmentPct", Math.round(100.0 * committedDelivered / committed));
        out.put("spilloverPct", Math.round(100.0 * (committed - committedDelivered) / committed));
        out.put("scopeChangePct", Math.round(100.0 * added / committed));
        out.put("approx", approx);
        // per-sprint series for the trend bars — zero-committed
        // sprints carry no signal and are skipped, mirroring velocitySoS
        List<Map<String, Object>> perSprint = new ArrayList<>();
        for (SprintStats s : closed) {
            if (s.committed() == 0) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", s.sprint().getName());
            row.put("commitmentPct", Math.round(100.0 * s.committedDelivered() / s.committed()));
            row.put("spilloverPct", Math.round(100.0 * (s.committed() - s.committedDelivered()) / s.committed()));
            row.put("scopeChangePct", Math.round(100.0 * s.addedAfterStart() / s.committed()));
            perSprint.add(row);
        }
        out.put("perSprint", perSprint);
        return out;
    }

    /**
     * W16 milestones, auto-derived from sprints — done = last closed sprints
     * with delivered work, upcoming = active/future sprints with end dates.
     * Zero manual input.
     */
    public Map<String, Object> clientMilestones(Long clientId) {
        List<SprintStats> stats = recentSprints(null, clientId, 8);
        List<Map<String, Object>> done = new ArrayList<>();
        List<Map<String, Object>> upcoming = new ArrayList<>();
        for (SprintStats s : stats) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", s.sprint().getName());
            if ("closed".equals(s.sprint().getState()) && s.delivered() > 0) {
                m.put("date", s.sprint().getCompleteDate() != null ? s.sprint().getCompleteDate() : s.sprint().getEndDate());
                m.put("detail", round1(s.delivered()) + " SP delivered" + (s.approx() ? " (approx)" : ""));
                done.add(m);
            } else if (!"closed".equals(s.sprint().getState())) {
                m.put("date", s.sprint().getEndDate());
                m.put("detail", s.members() + " items in scope");
                upcoming.add(m);
            }
        }
        // mock acctMilestones: max 3 done (most recent), max 4 upcoming
        Collections.reverse(done);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dataAvailable", !done.isEmpty() || !upcoming.isEmpty());
        out.put("done", done.subList(0, Math.min(3, done.size())));
        out.put("upcoming", upcoming.subList(0, Math.min(4, upcoming.size())));
        return out;
    }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }
}
