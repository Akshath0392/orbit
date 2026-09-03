package com.orbit.service.dashboard;

import com.orbit.domain.capacity.Developer;
import com.orbit.domain.capacity.ManDaySnapshot;
import com.orbit.domain.client.ManDayBudget;
import com.orbit.domain.client.Project;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes per-project risk score, slip probability, and signals from live DB data.
 * Algorithm per LLD §6.3 v1 heuristic.
 *
 * All inputs are bulk-preloaded into a {@link RiskContext} so scoring
 * N projects costs a fixed number of queries instead of ~10 per project.
 */
@Service
public class RiskScoringService {

    private final AlertRepository alerts;
    private final IssueMilestoneRepository milestones;
    private final JiraIssueRepository issues;
    private final ManDayBudgetRepository budgets;
    private final ManDaySnapshotRepository snapshots;
    private final DeveloperRepository developers;

    public RiskScoringService(AlertRepository alerts, IssueMilestoneRepository milestones,
                               JiraIssueRepository issues, ManDayBudgetRepository budgets,
                               ManDaySnapshotRepository snapshots, DeveloperRepository developers) {
        this.alerts = alerts; this.milestones = milestones; this.issues = issues;
        this.budgets = budgets; this.snapshots = snapshots; this.developers = developers;
    }

    // ── Preloaded inputs ──────────────────────────────────────────────────

    public record RiskContext(
        Map<Long,Long> projectCriticalAlerts,   // OPEN + ACKNOWLEDGED
        Map<Long,Long> projectRiskAlerts,       // OPEN
        Map<Long,Long> clientCriticalAlerts,    // OPEN (fallback for older seeded data)
        Map<Long,Long> clientRiskAlerts,        // OPEN
        Map<Long,Long> tbcMilestones,
        Map<Long,Long> overdueMilestones,
        Map<Long,Long> slaBreaches,
        Map<Long,List<JiraIssue>> holdingCrs,
        Map<Long,ManDayBudget> budgetByProject,
        Map<Long,List<ManDaySnapshot>> snapshotsByProject,   // newest-first
        Map<Long,Long> uatBlockersByClient,
        int teamLoadPct
    ) {}

    /** Bulk-fetch every scoring input for the given projects in 10 queries flat. */
    public RiskContext preload(List<Project> projects) {
        return preload(projects, developers.findAllByOrderByUtilizationDesc());
    }

    /** Variant for callers that already hold the developer list (radar). */
    public RiskContext preload(List<Project> projects, List<Developer> devList) {
        int teamLoadPct = devList.stream()
            .mapToInt(d -> d.getUtilization() != null ? d.getUtilization() : 0)
            .max().orElse(0);

        List<Long> pids = projects.stream().map(Project::getId).filter(Objects::nonNull).toList();
        List<Long> cids = projects.stream()
            .map(p -> p.getClient() != null ? p.getClient().getId() : null)
            .filter(Objects::nonNull).distinct().toList();

        if (pids.isEmpty()) {
            return new RiskContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), teamLoadPct);
        }

        Map<Long,Long> projCrit = new HashMap<>(), projRisk = new HashMap<>();
        alerts.countBySeverityAndStatusGroupedByProject(pids).forEach(r -> {
            Long pid = ((Number) r[0]).longValue();
            long count = ((Number) r[3]).longValue();
            if ("critical".equals(r[1]) && ("OPEN".equals(r[2]) || "ACKNOWLEDGED".equals(r[2])))
                projCrit.merge(pid, count, Long::sum);
            if ("risk".equals(r[1]) && "OPEN".equals(r[2]))
                projRisk.merge(pid, count, Long::sum);
        });

        Map<Long,Long> clientCrit = new HashMap<>(), clientRisk = new HashMap<>();
        if (!cids.isEmpty()) {
            alerts.countBySeverityAndStatusGroupedByClient(cids).forEach(r -> {
                Long cid = ((Number) r[0]).longValue();
                long count = ((Number) r[3]).longValue();
                if ("critical".equals(r[1]) && "OPEN".equals(r[2])) clientCrit.merge(cid, count, Long::sum);
                if ("risk".equals(r[1])     && "OPEN".equals(r[2])) clientRisk.merge(cid, count, Long::sum);
            });
        }

        Map<Long,List<JiraIssue>> holding = issues.findHoldingCrsByProjects(pids).stream()
            .collect(Collectors.groupingBy(j -> j.getProject().getId(),
                     LinkedHashMap::new, Collectors.toList()));

        Map<Long,ManDayBudget> budgetMap = new HashMap<>();
        budgets.findByProjectIdIn(pids).forEach(b -> {
            if (b.getProject() != null) budgetMap.put(b.getProject().getId(), b);
        });

        Map<Long,List<ManDaySnapshot>> snapMap = snapshots.findTop14PerProject(pids).stream()
            .collect(Collectors.groupingBy(s -> s.getProject().getId(),
                     LinkedHashMap::new, Collectors.toList()));

        Map<Long,Long> uatByClient = cids.isEmpty() ? Map.of()
            : toCountMap(issues.countByClientsTypeAndLifecycleStageNotGrouped(cids, "UAT_BUG", "Fixed"));

        return new RiskContext(projCrit, projRisk, clientCrit, clientRisk,
            toCountMap(milestones.countTbcGroupedByProject(pids)),
            toCountMap(milestones.countOverdueGroupedByProject(pids)),
            toCountMap(issues.countByProjectsTypeAndSlaStatusGrouped(pids, "PROD_BUG", "Breached")),
            holding, budgetMap, snapMap, uatByClient, teamLoadPct);
    }

    /** Score a single project (bulk-preloads its own context). */
    public ProjectRiskResult score(Project p) {
        return score(p, preload(List.of(p)));
    }

    /** Score one project against a preloaded context. */
    public ProjectRiskResult score(Project p, RiskContext ctx) {
        Long projectId = p.getId();
        Long clientId  = p.getClient() != null ? p.getClient().getId() : null;

        // ── Alert counts ────────────────────────────────────────────────
        long criticalAlerts = ctx.projectCriticalAlerts().getOrDefault(projectId, 0L);
        long riskAlerts     = ctx.projectRiskAlerts().getOrDefault(projectId, 0L);

        // Fallback: count via client if no project-level alerts exist (older seeded data uses client_id only)
        if (criticalAlerts == 0 && clientId != null) {
            criticalAlerts = ctx.clientCriticalAlerts().getOrDefault(clientId, 0L);
            riskAlerts     = ctx.clientRiskAlerts().getOrDefault(clientId, 0L);
        }

        // ── Milestone counts ─────────────────────────────────────────────
        long tbcMilestones     = ctx.tbcMilestones().getOrDefault(projectId, 0L);
        long overdueMilestones = ctx.overdueMilestones().getOrDefault(projectId, 0L);

        // ── SLA breaches (prod bugs) ──────────────────────────────────────
        long slaBreaches = ctx.slaBreaches().getOrDefault(projectId, 0L);

        // ── Hold aging ───────────────────────────────────────────────────
        List<JiraIssue> holdingCrs = ctx.holdingCrs().getOrDefault(projectId, List.of());
        long maxHoldDays = holdingCrs.stream()
            .filter(cr -> cr.getUpdatedAt() != null)
            .mapToLong(cr -> ChronoUnit.DAYS.between(cr.getUpdatedAt(), LocalDateTime.now()))
            .max().orElse(0);

        // ── Budget burn % ─────────────────────────────────────────────────
        double burnPct = computeBurnPct(projectId, ctx);

        // ── Team load % (max developer utilisation — proxy for project load) ──
        // We don't have per-project assignments in this sprint; use team-wide max as upper bound
        int teamLoadPct = ctx.teamLoadPct();

        // ── LLD §6.3 scoring formula ──────────────────────────────────────
        int score = 100;
        score -= (int)(criticalAlerts * 15);
        score -= (int)(riskAlerts     *  8);
        score -= (int)(overdueMilestones * 10);
        score -= (int)(tbcMilestones   *  3);
        score -= (int)(slaBreaches     * 12);
        score -= (maxHoldDays > 7 ? 8 : 0);
        score -= (burnPct > 80 ? 10 : burnPct > 60 ? 5 : 0);
        score -= (teamLoadPct > 85 ? 8 : 0);
        score = Math.max(0, Math.min(100, score));

        // ── Slip probability — shifted sigmoid so score=100 → ~12% and score=0 → ~99% ──
        // Shift of 30 centres the curve at score=70 (mid-watch) rather than score=50
        double slipProbability = sigmoid((100.0 - score - 30.0) / 15.0);
        int slipPct = (int) Math.round(slipProbability * 100);

        // ── Risk level ────────────────────────────────────────────────────
        String riskLevel = score >= 80 ? "healthy" : score >= 60 ? "watch" : "critical";

        // ── Signal labels (shown as chips on project card) ────────────────
        List<String> signals = buildSignals(projectId, clientId, burnPct, maxHoldDays,
            holdingCrs, tbcMilestones, slaBreaches, teamLoadPct, ctx);

        // ── Heat strip: 14-day burn % history ─────────────────────────────
        List<Integer> heat = computeHeatStrip(projectId, (int) burnPct, ctx);

        // ── Budget exhaustion date from latest snapshot ───────────────────
        String exhaustionDate = ctx.snapshotsByProject().getOrDefault(projectId, List.of())
            .stream().findFirst()
            .map(s -> s.getForecastExhaustion() != null ? s.getForecastExhaustion().toString() : "Unknown")
            .orElse("Unknown");

        // ── Insight text ──────────────────────────────────────────────────
        String insight = buildInsight(slipPct, riskLevel, burnPct, criticalAlerts,
            maxHoldDays, holdingCrs, tbcMilestones);

        return new ProjectRiskResult(riskLevel, slipPct, (int) burnPct, teamLoadPct,
            exhaustionDate, signals, heat, insight, score);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private double computeBurnPct(Long projectId, RiskContext ctx) {
        ManDayBudget b = ctx.budgetByProject().get(projectId);
        if (b == null) return 50.0;
        var snaps = ctx.snapshotsByProject().getOrDefault(projectId, List.of());
        if (snaps.isEmpty() || b.getPurchasedDays() == null) return 50.0;
        double burned    = snaps.get(0).getBurnedDays().doubleValue();
        double purchased = b.getPurchasedDays().doubleValue();
        return purchased > 0 ? burned / purchased * 100.0 : 0.0;
    }

    /** Build the 14-entry heat strip from burn snapshots, newest-first → oldest-first */
    private List<Integer> computeHeatStrip(Long projectId, int latestBurnPct, RiskContext ctx) {
        var snaps = ctx.snapshotsByProject().getOrDefault(projectId, List.of());
        List<Integer> heat = new ArrayList<>();
        for (var s : snaps) {
            double burned    = s.getBurnedDays() != null ? s.getBurnedDays().doubleValue() : 0;
            double remaining = s.getRemainingDays() != null ? s.getRemainingDays().doubleValue() : 1;
            double total     = burned + remaining;
            int pct = total > 0 ? (int)(burned / total * 100) : latestBurnPct;
            heat.add(pct);
        }
        Collections.reverse(heat);  // oldest→newest for display

        // Pad left with first value if < 14 points
        while (heat.size() < 14) heat.add(0, heat.isEmpty() ? latestBurnPct : heat.get(0));
        if (heat.size() > 14) heat = heat.subList(heat.size() - 14, heat.size());
        return heat;
    }

    private List<String> buildSignals(Long projectId, Long clientId, double burnPct,
                                       long maxHoldDays, List<JiraIssue> holdingCrs,
                                       long tbcMilestones, long slaBreaches, int teamLoadPct,
                                       RiskContext ctx) {
        List<String> signals = new ArrayList<>();

        // Budget signal
        var latestSnap = ctx.snapshotsByProject().getOrDefault(projectId, List.of())
            .stream().findFirst();
        latestSnap.ifPresent(s -> {
            if (s.getForecastExhaustion() != null && burnPct > 70) {
                LocalDate exh = s.getForecastExhaustion();
                long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), exh);
                if (daysLeft < 30) signals.add("Budget exhausts " + formatDate(exh));
            }
        });

        // SLA signal
        if (slaBreaches > 0)
            signals.add(slaBreaches == 1 ? "P0 SLA breached" : slaBreaches + " SLA breaches");

        // Hold signal
        if (!holdingCrs.isEmpty()) {
            String holdKey = holdingCrs.get(0).getIssueKey();
            signals.add(holdKey + " hold " + maxHoldDays + "d");
        }

        // TBC signal
        if (tbcMilestones > 0)
            signals.add(tbcMilestones + " TBC milestone" + (tbcMilestones > 1 ? "s" : ""));

        // Team load signal
        if (teamLoadPct > 85)
            signals.add("Lead dev " + teamLoadPct + "% load");

        // UAT blockers
        if (clientId != null) {
            long uatBugs = ctx.uatBlockersByClient().getOrDefault(clientId, 0L);
            if (uatBugs > 0) signals.add(uatBugs + " UAT blocker" + (uatBugs > 1 ? "s" : ""));
        }

        return signals.stream().limit(4).collect(Collectors.toList());
    }

    private String buildInsight(int slipPct, String riskLevel, double burnPct,
                                  long criticalAlerts, long maxHoldDays,
                                  List<JiraIssue> holdingCrs, long tbcMilestones) {
        StringBuilder sb = new StringBuilder();
        sb.append(slipPct).append("% slip probability. ");
        if ("critical".equals(riskLevel)) {
            if (burnPct > 85) sb.append("Budget most urgent — less than 15% remaining. ");
            if (criticalAlerts > 0) sb.append(criticalAlerts).append(" critical alert(s) unresolved. ");
            if (!holdingCrs.isEmpty()) sb.append(holdingCrs.get(0).getIssueKey())
                .append(" hold ").append(maxHoldDays).append(" days is a key blocker. ");
        } else if ("watch".equals(riskLevel)) {
            if (tbcMilestones > 0) sb.append(tbcMilestones).append(" milestone date(s) still TBC. ");
            if (burnPct > 60) sb.append("Burn rate elevated — monitor weekly. ");
        } else {
            sb.append("No critical blockers. All milestones on track.");
        }
        return sb.toString().trim();
    }

    private String formatDate(LocalDate d) {
        return d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
            + " " + d.getDayOfMonth();
    }

    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private static Map<Long,Long> toCountMap(List<Object[]> rows) {
        Map<Long,Long> out = new HashMap<>();
        rows.forEach(r -> out.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue()));
        return out;
    }

    // ── Result record ─────────────────────────────────────────────────────

    public record ProjectRiskResult(
        String riskLevel,
        int slipProbabilityPct,
        int burnPct,
        int loadPct,
        String forecastExhaustionDate,
        List<String> signals,
        List<Integer> heat,
        String insight,
        int rawScore
    ) {}
}
