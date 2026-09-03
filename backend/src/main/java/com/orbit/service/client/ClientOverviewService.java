package com.orbit.service.client;

import com.orbit.domain.capacity.ManDaySnapshot;
import com.orbit.domain.client.Client;
import com.orbit.domain.client.ManDayBudget;
import com.orbit.domain.client.Project;
import com.orbit.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Builds the client-list overview rows for GET /api/v1/clients.
 * Replaces the former per-client loop (~5 count queries + a burn loop per
 * client) with bulk queries regardless of client count. Row keys, values,
 * and the health formula are identical to the former ClientController.list().
 */
@Service
public class ClientOverviewService {

    private final ClientRepository clients;
    private final JiraIssueRepository issues;
    private final IssueMilestoneRepository milestones;
    private final AlertRepository alerts;
    private final ManDayBudgetRepository budgets;
    private final ManDaySnapshotRepository snapshots;
    private final ProjectRepository projects;

    public ClientOverviewService(ClientRepository clients, JiraIssueRepository issues,
            IssueMilestoneRepository milestones, AlertRepository alerts,
            ManDayBudgetRepository budgets, ManDaySnapshotRepository snapshots,
            ProjectRepository projects) {
        this.clients = clients; this.issues = issues; this.milestones = milestones;
        this.alerts = alerts; this.budgets = budgets; this.snapshots = snapshots;
        this.projects = projects;
    }

    @org.springframework.cache.annotation.Cacheable(com.orbit.config.CacheConfig.CLIENTS_LIST)
    public List<Map<String,Object>> clientOverviews() {
        List<Client> active = clients.findByActiveTrue();
        if (active.isEmpty()) return List.of();
        List<Long> cids = active.stream().map(Client::getId).toList();

        Map<Long,Long> crs  = toCountMap(issues.countByClientsAndTypeGrouped(cids, "CR"));
        Map<Long,Long> bugs = toCountMap(issues.countByClientsTypeAndSeverityInGrouped(cids, "PROD_BUG", List.of("P0","P1")));
        Map<Long,Long> tbc  = toCountMap(milestones.countTbcGroupedByClient(cids));

        Map<Long,Long> critAlerts = new HashMap<>(), riskAlerts = new HashMap<>();
        alerts.countBySeverityAndStatusGroupedByClient(cids).forEach(r -> {
            Long cid = ((Number) r[0]).longValue();
            long count = ((Number) r[3]).longValue();
            if ("OPEN".equals(r[2])) {
                if ("critical".equals(r[1])) critAlerts.merge(cid, count, Long::sum);
                if ("risk".equals(r[1]))     riskAlerts.merge(cid, count, Long::sum);
            }
        });

        Map<Long,Integer> burnByClient = computeBurnByClient(cids);

        List<Map<String,Object>> out = new ArrayList<>();
        for (Client c : active) {
            long crit = critAlerts.getOrDefault(c.getId(), 0L);
            long risk = riskAlerts.getOrDefault(c.getId(), 0L);
            long p0p1 = bugs.getOrDefault(c.getId(), 0L);
            long tbcCount = tbc.getOrDefault(c.getId(), 0L);
            int burn = burnByClient.getOrDefault(c.getId(), 0);
            int health = computeClientHealth(crit, risk, p0p1, tbcCount, burn);

            int green = c.getHealthGreenThreshold() != null ? c.getHealthGreenThreshold() : 80;
            int amber = c.getHealthAmberThreshold() != null ? c.getHealthAmberThreshold() : 60;
            String level = health >= green ? "healthy" : health >= amber ? "watch" : "critical";

            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",                   c.getId());
            m.put("name",                 c.getName());
            m.put("code",                 c.getCode());
            m.put("health",               health);
            m.put("healthLevel",          level);
            m.put("healthGreenThreshold", green);
            m.put("healthAmberThreshold", amber);
            m.put("crs",                  crs.getOrDefault(c.getId(), 0L));
            m.put("bugs",                 p0p1);
            m.put("tbc",                  tbcCount);
            m.put("burn",                 burn);
            m.put("contact",              c.getContactName() != null ? c.getContactName() : "");
            m.put("csatLaunch",           c.getCsatLaunch());
            m.put("csatBau",              c.getCsatBau());
            m.put("engagementScore",      c.getEngagementScore());
            out.add(m);
        }
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Per-client burn average — same formula as the former computeClientBurn:
     * per project, latest snapshot burned / budget purchased (missing data → 50),
     * averaged over the client's active projects (no projects → 0).
     */
    private Map<Long,Integer> computeBurnByClient(List<Long> cids) {
        List<Project> allProjects = projects.findByClientIdInAndActiveTrue(cids);
        if (allProjects.isEmpty()) return Map.of();
        List<Long> pids = allProjects.stream().map(Project::getId).toList();

        Map<Long,BigDecimal> purchasedByProject = new HashMap<>();
        for (ManDayBudget b : budgets.findByProjectIdIn(pids)) {
            if (b.getProject() != null) purchasedByProject.put(b.getProject().getId(), b.getPurchasedDays());
        }

        // Rows are ordered project_id, snapshot_date DESC — first row per project is the latest.
        Map<Long,BigDecimal> latestBurned = new HashMap<>();
        Set<Long> projectsWithSnapshots = new HashSet<>();
        for (ManDaySnapshot s : snapshots.findTop14PerProject(pids)) {
            Long pid = s.getProject() != null ? s.getProject().getId() : null;
            if (pid == null || projectsWithSnapshots.contains(pid)) continue;
            projectsWithSnapshots.add(pid);
            latestBurned.put(pid, s.getBurnedDays());
        }

        Map<Long,Integer> totals = new HashMap<>(), counts = new HashMap<>();
        for (Project p : allProjects) {
            Long cid = p.getClient() != null ? p.getClient().getId() : null;
            if (cid == null) continue;
            BigDecimal purchased = purchasedByProject.get(p.getId());
            BigDecimal burned    = latestBurned.get(p.getId());

            int projectBurn;
            if (projectsWithSnapshots.contains(p.getId()) && purchased != null && burned != null) {
                double b = burned.doubleValue(), pu = purchased.doubleValue();
                projectBurn = pu > 0 ? (int)(b / pu * 100) : 50;
            } else {
                projectBurn = 50;
            }
            totals.merge(cid, projectBurn, Integer::sum);
            counts.merge(cid, 1, Integer::sum);
        }

        Map<Long,Integer> out = new HashMap<>();
        totals.forEach((cid, total) -> out.put(cid, total / counts.get(cid)));
        return out;
    }

    private int computeClientHealth(long criticalAlerts, long riskAlerts,
                                     long p0p1Bugs, long tbcMilestones, int burnPct) {
        int score = 100;
        score -= (int)(criticalAlerts * 15);
        score -= (int)(riskAlerts     *  8);
        score -= (int)(p0p1Bugs       * 10);
        score -= (int)(tbcMilestones  *  3);
        score -= burnPct > 90 ? 20 : burnPct > 80 ? 12 : burnPct > 60 ? 5 : 0;
        return Math.max(0, Math.min(100, score));
    }

    private static Map<Long,Long> toCountMap(List<Object[]> rows) {
        Map<Long,Long> out = new HashMap<>();
        rows.forEach(r -> out.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue()));
        return out;
    }
}
