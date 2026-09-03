package com.orbit.service;

import com.orbit.domain.account.ProjectRelease;
import com.orbit.domain.account.ProjectRisk;
import com.orbit.domain.account.ProjectTeam;
import com.orbit.domain.client.Project;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Builds the full Account-Detail response shape consumed by the frontend AccountDetailPage.
 * Single read-only aggregator — chatty per-section APIs would slow this page down considerably.
 */
@Service
public class AccountDetailService {

    private static final List<String> CLOSED_BUG = List.of("Closed","Invalid","Resolved","Canceled");
    private static final List<String> CLOSED_CR  = List.of("Closed","Invalid","Canceled");

    private final ProjectRepository           projects;
    private final JiraIssueRepository         issues;
    private final ManDayBudgetRepository      budgets;
    private final ManDaySnapshotRepository    snapshots;
    private final ProjectTeamRepository       teams;
    private final ProjectRiskRepository       risks;
    private final ProjectReleaseRepository    releases;
    private final ProjectWinRepository        wins;
    private final GovernanceMeetingRepository governance;
    private final ProjectHealthService        health;
    private final StageSlaTargetRepository    slaTargets;
    private final com.orbit.service.dashboard.RiskScoringService riskScoring;
    private final AlertRepository             alerts;
    private final UatCycleRepository          uatCycles;

    public AccountDetailService(ProjectRepository projects,
                                 JiraIssueRepository issues,
                                 ManDayBudgetRepository budgets,
                                 ManDaySnapshotRepository snapshots,
                                 ProjectTeamRepository teams,
                                 ProjectRiskRepository risks,
                                 ProjectReleaseRepository releases,
                                 ProjectWinRepository wins,
                                 GovernanceMeetingRepository governance,
                                 ProjectHealthService health,
                                 StageSlaTargetRepository slaTargets,
                                 com.orbit.service.dashboard.RiskScoringService riskScoring,
                                 AlertRepository alerts,
                                 UatCycleRepository uatCycles) {
        this.projects = projects; this.issues = issues;
        this.budgets = budgets; this.snapshots = snapshots;
        this.teams = teams; this.risks = risks; this.releases = releases;
        this.wins = wins; this.governance = governance;
        this.health = health; this.slaTargets = slaTargets;
        this.riskScoring = riskScoring; this.alerts = alerts;
        this.uatCycles = uatCycles;
    }

    // ── W18 Sprint Scope (docs/plan/orbitter-am-widget-parity-plan.md) ───────
    // Phase-grouped delivery tracker: every open CR lands in exactly one phase
    // derived from its lifecycle stage; recently-delivered CRs (60d) close the
    // loop under Delivered. Sprint tags attach once sprint sync (F3) lands.

    private static final List<String> SS_PHASES =
        List.of("Solutioning", "Development", "QA", "Production release", "Delivered");

    private static String trackerPhase(String stage, LocalDateTime resolvedAt) {
        String s = stage == null ? "" : stage.trim().toLowerCase();
        if (resolvedAt != null || s.equals("released") || s.equals("closed")) return "Delivered";
        if (s.contains("uat") || s.contains("customer validation")
            || s.equals("fixed") || s.contains("ready for prod")) return "Production release";
        if (s.contains("qa") || s.contains("staging") || s.contains("pre-prod")) return "QA";
        if (s.contains("dev") && !s.contains("brd") || s.equals("in progress")
            || s.contains("hold") || s.equals("blocked")) return "Development";
        return "Solutioning";
    }

    public Optional<Map<String,Object>> sprintScope(Long projectId) {
        return projects.findById(projectId).map(p -> {
            Map<String,Integer> targetByStage = new HashMap<>();
            slaTargets.findAll().forEach(t -> targetByStage.put(t.getStage(), t.getTargetDays()));
            LocalDateTime now = LocalDateTime.now();

            Map<String,List<Map<String,Object>>> byPhase = new LinkedHashMap<>();
            SS_PHASES.forEach(ph -> byPhase.put(ph, new ArrayList<>()));
            int total = 0;
            for (Object[] r : issues.findSprintScopeRowsForProject(projectId, now.minusDays(60))) {
                String stage = (String) r[3];
                LocalDateTime created = (LocalDateTime) r[4];
                LocalDateTime resolved = (LocalDateTime) r[5];
                String phase = trackerPhase(stage, resolved);
                Integer target = stage == null ? null : targetByStage.get(stage);
                long age = created == null ? 0 : ChronoUnit.DAYS.between(created, now);
                String lc = stage == null ? "" : stage.trim().toLowerCase();

                Map<String,Object> row = new LinkedHashMap<>();
                row.put("key", r[0]);
                row.put("summary", r[1]);
                row.put("status", r[2]);
                row.put("stage", stage);
                row.put("owner", r[6]);
                row.put("ageDays", age);
                row.put("targetDays", target);
                row.put("sprint", r.length > 7 ? r[7] : null); // current sprint tag (F3)
                row.put("badge", resolved != null || "Delivered".equals(phase) ? "delivered"
                    : lc.contains("hold") || lc.equals("blocked") ? "on-hold"
                    : target != null && age > target ? "delayed" : "on-track");
                byPhase.get(phase).add(row);
                total++;
            }
            List<Map<String,Object>> phases = new ArrayList<>();
            for (String ph : SS_PHASES) {
                Map<String,Object> block = new LinkedHashMap<>();
                block.put("phase", ph);
                block.put("rows", byPhase.get(ph));
                block.put("count", byPhase.get(ph).size());
                phases.add(block);
            }
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("projectId", p.getId());
            out.put("phases", phases);
            out.put("total", total);
            return out;
        });
    }

    public Optional<Map<String,Object>> assemble(Long projectId) {
        return projects.findById(projectId).map(p -> {
            Map<String,Object> r = new LinkedHashMap<>();
            r.put("id",         p.getId());
            r.put("name",       p.getName());
            r.put("opsModel",         p.getOpsModel() != null ? p.getOpsModel() : "launch+bau");
            r.put("goLiveDate",       p.getGoLiveDate());
            r.put("accountType",      p.getAccountType());
            r.put("revenueExposure",  p.getRevenueExposure());
            r.put("contractEndDate",  p.getContractEndDate());

            Map<String,Object> client = new LinkedHashMap<>();
            if (p.getClient() != null) {
                client.put("id",   p.getClient().getId());
                client.put("name", p.getClient().getName());
                client.put("code", p.getClient().getCode());
            }
            r.put("client", client);

            Map<String,Object> portfolio = new LinkedHashMap<>();
            if (p.getPortfolio() != null) {
                portfolio.put("id",   p.getPortfolio().getId());
                portfolio.put("name", p.getPortfolio().getName());
            }
            r.put("portfolio", portfolio);

            // Health + stage + RAG
            var hr = health.compute(p);
            r.put("stage",     hr.stage());
            r.put("healthPct", hr.healthPct());
            r.put("rag",       hr.healthPct() < 50 ? "Red" : hr.healthPct() < 75 ? "Amber" : "Green");

            r.put("mandays",          buildMandays(p));
            r.put("milestones",       buildMilestones(p));
            r.put("weeklyStatus",     buildWeeklyStatus(p, hr));
            r.put("launchOps",        buildOpsBlock(p, "launch"));
            r.put("bauOps",           buildOpsBlock(p, "bau"));
            r.put("productionIssues", buildProductionIssues(p));
            r.put("launchStories",    buildWorklist(p, "CR",       "In dev",    null,   8));
            r.put("openCrs",          buildWorklist(p, "CR",       null,        "open", 10));
            r.put("productionBugs",   buildWorklist(p, "PROD_BUG", null,        "open", 10));
            r.put("uatBugs",          buildWorklist(p, "UAT_BUG",  null,        "open", 10));
            r.put("internalTeam",     buildInternalTeam(p));
            r.put("clientTeam",       buildClientTeam(p));
            r.put("health",           buildHealthBlock(p, hr));
            r.put("workbench",        buildWorkbench(p));
            r.put("riskRegister",     buildRiskRegister(p));
            r.put("winsRegister",     buildWinsRegister(p));
            r.put("supportOps",       buildSupportOps(p));
            r.put("governance",       buildGovernance(p));
            r.put("releaseCalendar",  buildReleaseCalendar(p));
            return r;
        });
    }

    private List<Map<String,Object>> buildWinsRegister(Project p) {
        return wins.findByProjectIdOrderByCreatedAtDesc(p.getId()).stream().map(w -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",           w.getId());
            m.put("win",          w.getWin());
            m.put("recognisedOn", w.getRecognisedOn());
            m.put("source",       w.getSource());
            return m;
        }).toList();
    }

    private Map<String,Object> buildSupportOps(Project p) {
        var all = issues.findByProjectIdAndIssueTypeOrderByUpdatedAtDesc(p.getId(), "PROD_BUG");
        long open = 0, inProg = 0, resolved = 0;
        for (var j : all) {
            String s = j.getLifecycleStage();
            if (s == null) { open++; continue; }
            if (CLOSED_BUG.contains(s)) { resolved++; }
            else if (s.toLowerCase().contains("progress") || s.toLowerCase().contains("dev")
                    || s.toLowerCase().contains("review")) { inProg++; }
            else { open++; }
        }
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("open",       open);
        m.put("inProgress", inProg);
        m.put("resolved",   resolved);
        return m;
    }

    private List<Map<String,Object>> buildGovernance(Project p) {
        return governance.findByProjectIdOrderByNextDueAsc(p.getId()).stream().map(g -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",       g.getId());
            m.put("cadence",  g.getCadence());
            m.put("title",    g.getTitle());
            m.put("lastHeld", g.getLastHeld());
            m.put("nextDue",  g.getNextDue());
            m.put("owner",    g.getOwner());
            m.put("status",   g.getStatus());
            return m;
        }).toList();
    }

    // ── Mandays ───────────────────────────────────────────────────────────────

    private Map<String,Object> buildMandays(Project p) {
        var budget   = budgets.findByProjectId(p.getId());
        var snapshot = snapshots.findByProjectIdOrderBySnapshotDateDesc(p.getId()).stream().findFirst();
        Map<String,Object> m = new LinkedHashMap<>();
        if (budget.isEmpty()) {
            m.put("purchased", 0); m.put("consumed", 0); m.put("remaining", 0);
            m.put("consumedPct", 0); m.put("status", "amber");
            return m;
        }
        BigDecimal purchased = budget.get().getPurchasedDays() != null ? budget.get().getPurchasedDays() : BigDecimal.ZERO;
        BigDecimal consumed  = snapshot.map(s -> s.getBurnedDays() != null ? s.getBurnedDays() : BigDecimal.ZERO).orElse(BigDecimal.ZERO);
        BigDecimal remaining = purchased.subtract(consumed).max(BigDecimal.ZERO);
        int pct = purchased.signum() == 0 ? 0
            : consumed.multiply(BigDecimal.valueOf(100)).divide(purchased, 0, RoundingMode.HALF_UP).intValue();
        String status = pct >= 90 ? "red" : pct >= 65 ? "amber" : "green";
        m.put("purchased",   purchased);
        m.put("consumed",    consumed);
        m.put("remaining",   remaining);
        m.put("consumedPct", pct);
        m.put("status",      status);
        return m;
    }

    // ── Milestones (BRD → FSD → Dev → QA → UAT → Prod) ────────────────────────

    private List<Map<String,Object>> buildMilestones(Project p) {
        // Currently derived from issue_milestones if seeded; else show stage skeleton.
        String[] phases = {"Req Sign-off", "Design", "Dev Completion", "UAT Start", "UAT Sign-off", "Go-Live"};
        String stage = health.resolveStage(p);
        // Heuristic state derivation by life-stage
        String[] states = switch (stage) {
            case "PRE_LAUNCH"   -> new String[]{"done","done","current","pending","pending","pending"};
            case "HYPERCARE"    -> new String[]{"done","done","done","done","done","done"};
            case "STEADY_STATE" -> new String[]{"done","done","done","done","done","done"};
            default             -> new String[]{"done","done","current","pending","pending","pending"};
        };
        List<Map<String,Object>> out = new ArrayList<>();
        for (int i = 0; i < phases.length; i++) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("name",  phases[i]);
            m.put("state", states[i]);
            out.add(m);
        }
        return out;
    }

    // ── Weekly status (computed bullets from real signals) ────────────────────

    private Map<String,Object> buildWeeklyStatus(Project p, ProjectHealthService.HealthResult hr) {
        Map<String,Object> w = new LinkedHashMap<>();
        w.put("completionPct", hr.healthPct());
        List<String> bullets = new ArrayList<>();
        long openCrs = issues.countOpenByProjectAndType(p.getId(), "CR", CLOSED_CR);
        long openProdBugs = issues.countOpenByProjectAndType(p.getId(), "PROD_BUG", CLOSED_BUG);
        long openUatBugs  = issues.countOpenByProjectAndType(p.getId(), "UAT_BUG", CLOSED_BUG);
        bullets.add(openCrs + " open CRs");
        if (openProdBugs > 0) bullets.add(openProdBugs + " open production bug" + (openProdBugs == 1 ? "" : "s"));
        if (openUatBugs  > 0) bullets.add(openUatBugs  + " open UAT bug"        + (openUatBugs  == 1 ? "" : "s"));
        bullets.add("Stage: " + hr.stage().toLowerCase().replace('_', ' '));
        w.put("bullets", bullets);
        return w;
    }

    // ── Launch / BAU ops blocks (Launch = CR ticket workflow; BAU = bugs) ────

    private Map<String,Object> buildOpsBlock(Project p, String mode) {
        String type = "launch".equals(mode) ? "CR" : "PROD_BUG";
        var rows = issues.findByProjectIdAndIssueTypeOrderByUpdatedAtDesc(p.getId(), type);
        Map<String,Object> m = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            m.put("backlog",0); m.put("inProgress",0); m.put("closed",0);
            m.put("progressPct",0);
            return m;
        }
        long backlog = rows.stream().filter(j -> isBacklog(j.getLifecycleStage())).count();
        long inProg  = rows.stream().filter(j -> isInProgress(j.getLifecycleStage())).count();
        long closed  = rows.stream().filter(j -> j.getLifecycleStage() != null
            && (j.getLifecycleStage().equalsIgnoreCase("Closed") || j.getLifecycleStage().equalsIgnoreCase("Released"))).count();
        long total = rows.size();
        int progressPct = total > 0 ? (int) Math.round(closed * 100.0 / total) : 0;
        m.put("backlog",     backlog);
        m.put("inProgress",  inProg);
        m.put("closed",      closed);
        m.put("progressPct", progressPct);
        // launch/BAU date markers
        if ("launch".equals(mode)) {
            m.put("startDate", p.getGoLiveDate() != null ? p.getGoLiveDate().minusMonths(6) : null);
            m.put("endDate",   p.getGoLiveDate());
        } else {
            LocalDateTime lastUat = rows.stream()
                .filter(j -> j.getLifecycleStage() != null && j.getLifecycleStage().toLowerCase().contains("uat"))
                .map(JiraIssue::getUpdatedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
            m.put("lastUatSignOff", lastUat != null ? lastUat.toLocalDate() : null);
            m.put("lastGoLive",     p.getGoLiveDate());
        }
        return m;
    }

    // ── Production issues tracker ─────────────────────────────────────────────

    private Map<String,Object> buildProductionIssues(Project p) {
        var open  = issues.findByProjectIdAndIssueTypeOrderByUpdatedAtDesc(p.getId(), "PROD_BUG").stream()
            .filter(j -> j.getLifecycleStage() == null || !CLOSED_BUG.contains(j.getLifecycleStage())).toList();
        var closed= issues.findByProjectIdAndIssueTypeOrderByUpdatedAtDesc(p.getId(), "PROD_BUG").stream()
            .filter(j -> j.getLifecycleStage() != null && CLOSED_BUG.contains(j.getLifecycleStage())).toList();

        Map<String,Object> m = new LinkedHashMap<>();
        m.put("totalOpen", open.size());
        m.put("closed",    closed.size());
        m.put("s1", countSev(open, "P0"));
        m.put("s2", countSev(open, "P1"));
        m.put("s3", countSev(open, "P2"));
        m.put("s4", countSev(open, "P3"));
        m.put("avgAgeing", avgAgeingDays(open));

        Map<String,Long> stat = new LinkedHashMap<>();
        for (var j : open) stat.merge(j.getLifecycleStage() != null ? j.getLifecycleStage() : "Unknown", 1L, Long::sum);
        m.put("statusBreakdown", stat);

        Map<String,Long> ageBuckets = new LinkedHashMap<>();
        ageBuckets.put("0-30",   0L); ageBuckets.put("31-90", 0L);
        ageBuckets.put("91-180", 0L); ageBuckets.put("180+",  0L);
        for (var j : open) {
            long d = ageingDays(j);
            String b = d <= 30 ? "0-30" : d <= 90 ? "31-90" : d <= 180 ? "91-180" : "180+";
            ageBuckets.merge(b, 1L, Long::sum);
        }
        m.put("ageingBuckets", ageBuckets);

        List<Map<String,Object>> rows = new ArrayList<>();
        for (var j : open.stream().limit(20).toList()) {
            Map<String,Object> r = new LinkedHashMap<>();
            r.put("key",      j.getIssueKey());
            r.put("summary",  j.getSummary());
            r.put("severity", j.getSeverity());
            r.put("status",   j.getLifecycleStage());
            r.put("ageing",   ageingDays(j));
            r.put("owner",    j.getAssigneeName() != null ? j.getAssigneeName() : "—");
            r.put("eta",      null);
            rows.add(r);
        }
        m.put("rows", rows);
        return m;
    }

    // ── Worklists (launch stories / open CRs / prod bugs / uat bugs) ─────────

    private List<Map<String,Object>> buildWorklist(Project p, String type, String stageFilter, String openFilter, int limit) {
        var rows = issues.findByProjectIdAndIssueTypeOrderByUpdatedAtDesc(p.getId(), type).stream()
            .filter(j -> {
                if (stageFilter != null && (j.getLifecycleStage() == null
                    || !j.getLifecycleStage().equalsIgnoreCase(stageFilter))) return false;
                if ("open".equals(openFilter)) {
                    var closed = type.endsWith("_BUG") ? CLOSED_BUG : CLOSED_CR;
                    if (j.getLifecycleStage() != null && closed.contains(j.getLifecycleStage())) return false;
                }
                return true;
            })
            .limit(limit).toList();
        List<Map<String,Object>> out = new ArrayList<>();
        for (var j : rows) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("key",        j.getIssueKey());
            m.put("summary",    j.getSummary());
            m.put("status",     j.getLifecycleStage());
            m.put("owner",      j.getAssigneeName() != null ? j.getAssigneeName() : "—");
            m.put("severity",   j.getSeverity());
            m.put("ageing",     ageingDays(j));
            m.put("targetDate", null);   // not tracked yet
            out.add(m);
        }
        return out;
    }

    // ── Team blocks ───────────────────────────────────────────────────────────

    private Map<String,Object> buildInternalTeam(Project p) {
        var team = teams.findByProjectId(p.getId()).orElse(new ProjectTeam());
        Map<String,Object> t = new LinkedHashMap<>();
        t.put("projectManager",     nv(team.getInternalPm()));
        t.put("accountManager",     nv(team.getInternalAm()));
        t.put("engineeringManager", nv(team.getInternalEm()));
        t.put("solutionsManager",   nv(team.getInternalSol()));
        t.put("techLead",           nv(team.getInternalTechLead()));
        t.put("qaLead",             nv(team.getInternalQaLead()));
        t.put("supportManager",     nv(team.getInternalSupportMgr()));
        return t;
    }

    private Map<String,Object> buildClientTeam(Project p) {
        var team = teams.findByProjectId(p.getId()).orElse(new ProjectTeam());
        Map<String,Object> t = new LinkedHashMap<>();
        t.put("executiveSponsor", nv(team.getClientSponsor()));
        t.put("techSpoc",         nv(team.getClientTechSpoc()));
        t.put("businessSpoc",     nv(team.getClientBizSpoc()));
        t.put("projectManager",   nv(team.getClientPm()));
        return t;
    }

    // ── Health block (computed from health signals — narrative inferred) ─────

    private Map<String,Object> buildHealthBlock(Project p, ProjectHealthService.HealthResult hr) {
        Map<String,Object> h = new LinkedHashMap<>();
        // Pick top 3 deductions as the reasons text
        var topSignals = hr.signals().stream()
            .filter(s -> s.deduction() > 0)
            .sorted((a, b) -> Double.compare(b.deduction(), a.deduction()))
            .limit(3).toList();
        List<String> reasons = new ArrayList<>();
        for (var s : topSignals) reasons.add(humanise(s.metric()) + " contributing " + Math.round(s.deduction()) + " pts");
        if (reasons.isEmpty()) reasons.add("No major signals — all metrics healthy");

        Map<String,Object> sentiment = new LinkedHashMap<>();
        sentiment.put("score", BigDecimal.valueOf(hr.healthPct() / 10.0).setScale(1, RoundingMode.HALF_UP));
        sentiment.put("label", hr.healthPct() >= 75 ? "Positive" : hr.healthPct() >= 50 ? "Neutral" : "Negative");
        sentiment.put("reasons", reasons);
        h.put("sentiment", sentiment);

        // mock acctSentimentCard: Schedule confidence (banded slip probability, v1
        // heuristic) + Escalations (open) — open alerts on this project
        int slipPct = riskScoring.score(p).slipProbabilityPct();
        h.put("scheduleConfidence", slipPct < 30 ? "High" : slipPct <= 60 ? "Medium" : "Low");
        h.put("slipProbabilityPct", slipPct);
        h.put("escalationsOpen", alerts.countByProjectIdAndStatus(p.getId(), "OPEN"));

        long uatOpen = issues.countOpenByProjectAndType(p.getId(), "UAT_BUG", CLOSED_BUG);
        long uatClosed = issues.findByProjectIdAndIssueTypeOrderByUpdatedAtDesc(p.getId(), "UAT_BUG").stream()
            .filter(j -> j.getLifecycleStage() != null && CLOSED_BUG.contains(j.getLifecycleStage())).count();
        Map<String,Object> delivery = new LinkedHashMap<>();
        delivery.put("uatItems",   uatOpen + uatClosed);
        delivery.put("signedOff",  uatClosed);
        h.put("deliverySpeed", delivery);

        long prodOpen = issues.countOpenByProjectAndType(p.getId(), "PROD_BUG", CLOSED_BUG);
        long prodClosed = issues.findByProjectIdAndIssueTypeOrderByUpdatedAtDesc(p.getId(), "PROD_BUG").stream()
            .filter(j -> j.getLifecycleStage() != null && CLOSED_BUG.contains(j.getLifecycleStage())).count();
        Map<String,Object> stability = new LinkedHashMap<>();
        stability.put("bugsReported", prodOpen + prodClosed);
        stability.put("bugsClosed",   prodClosed);
        h.put("platformStability", stability);

        return h;
    }

    // ── Workbench (mock wb-cards: This Week / Next Week / Attention) ─────────

    private Map<String,Object> buildWorkbench(Project p) {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        Map<String,Long> resolvedByType = new HashMap<>();
        for (Object[] r : issues.countResolvedSinceByType(p.getId(), weekAgo))
            resolvedByType.put((String) r[0], ((Number) r[1]).longValue());

        Map<String,Object> thisWeek = new LinkedHashMap<>();
        thisWeek.put("crsClosed", resolvedByType.getOrDefault("CR", 0L));
        thisWeek.put("bugsFixed", resolvedByType.getOrDefault("PROD_BUG", 0L)
                                + resolvedByType.getOrDefault("UAT_BUG", 0L));
        thisWeek.put("uatSignOffs", p.getClient() == null ? 0L
            : uatCycles.countByIssueClientIdAndSignOffStatus(p.getClient().getId(), "SIGNED_OFF"));

        Map<String,Object> nextWeek = new LinkedHashMap<>();
        nextWeek.put("goLives", releases.findByProjectIdAndReleaseDateBetweenOrderByReleaseDateAsc(
            p.getId(), LocalDate.now(), LocalDate.now().plusDays(7)).size());
        nextWeek.put("uatCycles", p.getClient() == null ? 0L
            : uatCycles.countByIssueClientIdAndSignOffStatus(p.getClient().getId(), "PENDING"));
        nextWeek.put("signOffsDue", issues.countOpenCrsByStageLike(p.getId(), "%client approval%"));

        Map<String,Object> attention = new LinkedHashMap<>();
        attention.put("blocked", issues.countOpenCrsByStageLike(p.getId(), "hold"));
        attention.put("awaitingClient", issues.countOpenCrsByStageLike(p.getId(), "%awaited%"));
        attention.put("escalations", alerts.countByProjectIdAndStatus(p.getId(), "OPEN"));

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("thisWeek", thisWeek);
        out.put("nextWeek", nextWeek);
        out.put("attention", attention);
        return out;
    }

    // ── Risk register + Release calendar ──────────────────────────────────────

    private List<Map<String,Object>> buildRiskRegister(Project p) {
        return risks.findByProjectIdOrderByCreatedAtDesc(p.getId()).stream().map(r -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",          r.getId());
            m.put("jiraTicket",  r.getJiraTicket());
            m.put("risk",        r.getRisk());
            m.put("receivedOn",  r.getReceivedOn());
            m.put("rag",         r.getRag());
            m.put("actionEnd",   r.getActionEnd());
            m.put("actionOwner", r.getActionOwner());
            m.put("source",      r.getSource());
            return m;
        }).toList();
    }

    private List<Map<String,Object>> buildReleaseCalendar(Project p) {
        LocalDate from = LocalDate.now().minusDays(14);
        LocalDate to   = LocalDate.now().plusMonths(2);
        return releases.findByProjectIdAndReleaseDateBetweenOrderByReleaseDateAsc(p.getId(), from, to).stream().map(r -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",    r.getId());
            m.put("date",  r.getReleaseDate());
            m.put("type",  r.getReleaseType());
            m.put("label", r.getLabel());
            m.put("rag",   r.getRag());
            return m;
        }).toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static long countSev(List<JiraIssue> rows, String sev) {
        return rows.stream().filter(j -> sev.equals(j.getSeverity())).count();
    }
    private static long ageingDays(JiraIssue j) {
        if (j.getCreatedAt() == null) return 0;
        return ChronoUnit.DAYS.between(j.getCreatedAt().toLocalDate(), LocalDate.now());
    }
    private static long avgAgeingDays(List<JiraIssue> rows) {
        if (rows.isEmpty()) return 0;
        return Math.round(rows.stream().mapToLong(AccountDetailService::ageingDays).average().orElse(0));
    }
    private static boolean isBacklog(String stage) {
        if (stage == null) return true;
        String s = stage.toLowerCase();
        return s.contains("backlog") || s.equals("to do") || s.contains("awaited") || s.contains("hold");
    }
    private static boolean isInProgress(String stage) {
        if (stage == null) return false;
        String s = stage.toLowerCase();
        return s.contains("in progress") || s.contains("in dev") || s.contains("review");
    }
    private static String nv(String s) { return s != null ? s : ""; }
    private static String humanise(String metric) {
        return switch (metric) {
            case "prod_bug_p0"      -> "P0 production bugs";
            case "prod_bug_p1"      -> "P1 production bugs";
            case "sla_breach"       -> "SLA breaches";
            case "cr_on_hold_pct"   -> "CRs on hold";
            case "uat_bug_count"    -> "UAT bug volume";
            case "manday_burn_risk" -> "Manday burn risk";
            default                 -> metric;
        };
    }
}
