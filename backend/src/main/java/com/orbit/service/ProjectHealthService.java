package com.orbit.service;

import com.orbit.domain.client.Project;
import com.orbit.domain.config.HealthProfileWeight;
import com.orbit.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Computes project health score based on life-stage-specific metric weights.
 *
 * Stage resolution:
 *   1. project.healthStage (manual override) if set
 *   2. Auto-inferred from go_live_date:
 *      - null or future  → PRE_LAUNCH
 *      - within 90 days  → HYPERCARE
 *      - otherwise       → STEADY_STATE
 *
 * For each metric the service computes a 0.0–1.0 normalised value, then:
 *   penalty = Σ(metricValue × weight)
 *   healthPct = clamp(100 − penalty, 0, 100)
 *
 * Normalization uses a sensitivity coefficient stored per weight row:
 *   metricValue = min(1.0, rawValue × sensitivity)
 * Higher sensitivity → the metric reaches full deduction faster.
 */
@Service
public class ProjectHealthService {

    public static final String PRE_LAUNCH   = "PRE_LAUNCH";
    public static final String HYPERCARE    = "HYPERCARE";
    public static final String STEADY_STATE = "STEADY_STATE";
    public static final String AT_RISK      = "AT_RISK";

    public static final List<String> ALL_STAGES  = List.of(PRE_LAUNCH, HYPERCARE, STEADY_STATE, AT_RISK);
    public static final List<String> ALL_METRICS = List.of(
        "prod_bug_p0", "prod_bug_p1", "sla_breach",
        "cr_on_hold_pct", "uat_bug_count", "manday_burn_risk"
    );

    private static final List<String> CLOSED_BUG = List.of("Closed","Invalid","Resolved","Canceled");
    private static final List<String> CLOSED_CR  = List.of("Closed","Invalid","Canceled");
    private static final List<String> HOLD_STAGES = List.of("Hold","Client Hold");

    private final HealthProfileWeightRepository weightRepo;
    private final JiraIssueRepository           issues;
    private final ManDayBudgetRepository        budgets;
    private final ManDaySnapshotRepository      snapshots;

    public ProjectHealthService(HealthProfileWeightRepository weightRepo,
                                 JiraIssueRepository issues,
                                 ManDayBudgetRepository budgets,
                                 ManDaySnapshotRepository snapshots) {
        this.weightRepo = weightRepo; this.issues = issues;
        this.budgets = budgets; this.snapshots = snapshots;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public record HealthResult(int healthPct, String stage, List<SignalDetail> signals) {}
    public record SignalDetail(String metric, double rawValue, double normValue, int weight, double deduction) {}

    /**
     * Preloaded per-project inputs so N projects cost a fixed number of queries
     * instead of ~8 queries each.
     */
    public record HealthContext(
        Map<String, Map<String,HealthProfileWeight>> weightsByStage,
        Map<Long, Long> p0Bugs,
        Map<Long, Long> p1Bugs,
        Map<Long, Long> slaBreaches,
        Map<Long, Long> openCrs,
        Map<Long, Long> holdCrs,
        Map<Long, Long> openUatBugs,
        Map<Long, BigDecimal> latestBurnedDays,
        Map<Long, BigDecimal> purchasedDays
    ) {}

    /** Bulk-fetch every health input for the given projects in 8 queries flat. */
    public HealthContext preloadContext(List<Project> projects) {
        Map<String, Map<String,HealthProfileWeight>> weightsByStage = new HashMap<>();
        weightRepo.findAllByOrderByStageAscMetricAsc().forEach(w ->
            weightsByStage.computeIfAbsent(w.getStage(), k -> new HashMap<>()).put(w.getMetric(), w));

        List<Long> pids = projects.stream().map(Project::getId).filter(Objects::nonNull).toList();
        if (pids.isEmpty()) {
            return new HealthContext(weightsByStage, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of());
        }

        Map<Long,Long> p0 = new HashMap<>(), p1 = new HashMap<>();
        issues.countOpenByProjectsTypeAndSeverityGrouped(pids, "PROD_BUG", CLOSED_BUG).forEach(r -> {
            Long pid = ((Number) r[0]).longValue();
            long count = ((Number) r[2]).longValue();
            if ("P0".equals(r[1])) p0.merge(pid, count, Long::sum);
            if ("P1".equals(r[1])) p1.merge(pid, count, Long::sum);
        });

        Map<Long,BigDecimal> burned = new HashMap<>();
        // Rows are ordered project_id, snapshot_date DESC — first row per project is the latest.
        snapshots.findTop14PerProject(pids).forEach(s -> {
            Long pid = s.getProject() != null ? s.getProject().getId() : null;
            if (pid != null && !burned.containsKey(pid) && s.getBurnedDays() != null)
                burned.put(pid, s.getBurnedDays());
        });

        Map<Long,BigDecimal> purchased = new HashMap<>();
        budgets.findByProjectIdIn(pids).forEach(b -> {
            if (b.getProject() != null && b.getPurchasedDays() != null)
                purchased.put(b.getProject().getId(), b.getPurchasedDays());
        });

        return new HealthContext(
            weightsByStage, p0, p1,
            toCountMap(issues.countByProjectsTypeAndSlaStatusGrouped(pids, "PROD_BUG", "Breached")),
            toCountMap(issues.countOpenByProjectsAndTypeGrouped(pids, "CR", CLOSED_CR)),
            toCountMap(issues.countByProjectsTypeAndLifecycleStagesGrouped(pids, "CR", HOLD_STAGES)),
            toCountMap(issues.countOpenByProjectsAndTypeGrouped(pids, "UAT_BUG", CLOSED_BUG)),
            burned, purchased);
    }

    /** Compute health for a single project (bulk-preloads its own context). */
    public HealthResult compute(Project project) {
        return compute(project, preloadContext(List.of(project)));
    }

    /** Compute health for one project against a preloaded context. */
    public HealthResult compute(Project project, HealthContext ctx) {
        String stage   = resolveStage(project);
        Map<String,HealthProfileWeight> weightMap =
            ctx.weightsByStage().getOrDefault(stage, Map.of());

        List<SignalDetail> signals = new ArrayList<>();
        double totalPenalty = 0;

        for (String metric : ALL_METRICS) {
            HealthProfileWeight w = weightMap.get(metric);
            int weight = w != null ? w.getWeight() : 0;
            if (weight == 0) {
                signals.add(new SignalDetail(metric, 0, 0, 0, 0));
                continue;
            }
            double sensitivity = w.getSensitivity() != null ? w.getSensitivity().doubleValue() : 1.0;
            double raw  = rawMetricValue(metric, project, ctx);
            double norm = Math.min(1.0, raw * sensitivity);
            double ded  = norm * weight;
            totalPenalty += ded;
            signals.add(new SignalDetail(metric, raw, norm, weight, ded));
        }

        int healthPct = (int) Math.round(Math.max(0, Math.min(100, 100 - totalPenalty)));
        return new HealthResult(healthPct, stage, signals);
    }

    /** Convenience: just the score (for bulk computation). */
    public int healthPct(Project project) { return compute(project).healthPct(); }

    /** Scores for many projects from one preloaded context. */
    public Map<Long,Integer> healthPctAll(List<Project> projects) {
        HealthContext ctx = preloadContext(projects);
        Map<Long,Integer> out = new HashMap<>();
        for (Project p : projects) out.put(p.getId(), compute(p, ctx).healthPct());
        return out;
    }

    /** Resolve the effective stage for a project. */
    public String resolveStage(Project project) {
        if (project.getHealthStage() != null && !project.getHealthStage().isBlank())
            return project.getHealthStage();
        LocalDate goLive = project.getGoLiveDate();
        if (goLive == null || goLive.isAfter(LocalDate.now())) return PRE_LAUNCH;
        long daysSince = ChronoUnit.DAYS.between(goLive, LocalDate.now());
        return daysSince <= 90 ? HYPERCARE : STEADY_STATE;
    }

    // ── Raw metric computation (returns 0.0 – unbounded; sensitivity caps to 1.0) ─

    private double rawMetricValue(String metric, Project project, HealthContext ctx) {
        Long pid = project.getId();
        return switch (metric) {

            case "prod_bug_p0" ->
                // 2 P0 bugs → sensitivity 0.5 → normValue 1.0 (full deduction)
                ctx.p0Bugs().getOrDefault(pid, 0L);

            case "prod_bug_p1" ->
                // ~3-4 P1 bugs → sensitivity 0.3 → normValue ~1.0
                ctx.p1Bugs().getOrDefault(pid, 0L);

            case "sla_breach" ->
                // 2 breaches → sensitivity 0.5 → normValue 1.0
                ctx.slaBreaches().getOrDefault(pid, 0L);

            case "cr_on_hold_pct" -> {
                long total = ctx.openCrs().getOrDefault(pid, 0L);
                if (total == 0) yield 0.0;
                long onHold = ctx.holdCrs().getOrDefault(pid, 0L);
                // ratio 0–1; sensitivity 1.5 → 0.67 hold rate → full deduction
                yield (double) onHold / total;
            }

            case "uat_bug_count" ->
                // 10 UAT bugs → sensitivity 0.1 → normValue 1.0
                ctx.openUatBugs().getOrDefault(pid, 0L) / 10.0;

            case "manday_burn_risk" -> {
                BigDecimal purchased = ctx.purchasedDays().get(pid);
                BigDecimal burned    = ctx.latestBurnedDays().get(pid);
                if (burned == null || purchased == null || purchased.doubleValue() == 0) yield 0.0;
                double burnPct = burned.doubleValue() / purchased.doubleValue() * 100;
                // activates above 80%; at 100% burn → raw = 1.0; sensitivity 1.0 → normValue 1.0
                yield Math.max(0, (burnPct - 80) / 20.0);
            }

            default -> 0.0;
        };
    }

    private static Map<Long,Long> toCountMap(List<Object[]> rows) {
        Map<Long,Long> out = new HashMap<>();
        rows.forEach(r -> out.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue()));
        return out;
    }

    /** All weights for all stages — used by the admin UI. */
    public Map<String, List<Map<String,Object>>> allWeightsGrouped() {
        Map<String, List<Map<String,Object>>> result = new LinkedHashMap<>();
        for (String stage : ALL_STAGES) result.put(stage, new ArrayList<>());
        weightRepo.findAllByOrderByStageAscMetricAsc().forEach(w -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",          w.getId());
            m.put("metric",      w.getMetric());
            m.put("weight",      w.getWeight());
            m.put("sensitivity", w.getSensitivity());
            result.computeIfAbsent(w.getStage(), k -> new ArrayList<>()).add(m);
        });
        return result;
    }

    public void upsertWeight(String stage, String metric, int weight, BigDecimal sensitivity) {
        HealthProfileWeight w = weightRepo.findByStageAndMetric(stage, metric)
            .orElseGet(() -> { var nw = new HealthProfileWeight(); nw.setStage(stage); nw.setMetric(metric); return nw; });
        w.setWeight(weight);
        if (sensitivity != null) w.setSensitivity(sensitivity);
        weightRepo.save(w);
    }
}
