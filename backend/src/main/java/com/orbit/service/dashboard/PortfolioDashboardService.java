package com.orbit.service.dashboard;

import com.orbit.domain.client.Portfolio;
import com.orbit.domain.client.Project;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.PortfolioRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.service.ProjectHealthService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Builds the four portfolio dashboard payloads (summary / kpis / accounts /
 * exceptions) from one bulk preload. Payload keys and values are
 * byte-identical to the former per-endpoint loops in PortfolioController —
 * the controller endpoints now return slices of {@link #dashboard(Long)}.
 */
@Service
public class PortfolioDashboardService {

    private static final List<String> CLOSED_CR  = List.of("Closed","Invalid","Canceled");
    private static final List<String> CLOSED_BUG = List.of("Closed","Invalid","Resolved","Canceled");

    private final PortfolioRepository  portfolios;
    private final ProjectRepository    projects;
    private final JiraIssueRepository  issues;
    private final ProjectHealthService health;

    public PortfolioDashboardService(PortfolioRepository portfolios, ProjectRepository projects,
                                     JiraIssueRepository issues, ProjectHealthService health) {
        this.portfolios = portfolios; this.projects = projects;
        this.issues = issues; this.health = health;
    }

    /**
     * All four payloads for one portfolio, or null when the portfolio doesn't
     * exist (controller maps null → 404; nulls are never cached).
     */
    @org.springframework.cache.annotation.Cacheable(
        cacheNames = com.orbit.config.CacheConfig.PORTFOLIO_DASHBOARD,
        key = "#portfolioId", unless = "#result == null")
    public Map<String,Object> dashboard(Long portfolioId) {
        Portfolio portfolio = portfolios.findById(portfolioId).orElse(null);
        if (portfolio == null) return null;

        List<Project> projs = projects.findByPortfolioIdAndActiveTrue(portfolioId);
        List<Long> pids = projs.stream().map(Project::getId).toList();

        Map<Long,Long> openCrs      = pids.isEmpty() ? Map.of() : toCountMap(issues.countOpenByProjectsAndTypeGrouped(pids, "CR",       CLOSED_CR));
        Map<Long,Long> openBugs     = pids.isEmpty() ? Map.of() : toCountMap(issues.countOpenByProjectsAndTypeGrouped(pids, "PROD_BUG", CLOSED_BUG));
        Map<Long,Long> totalCrs     = pids.isEmpty() ? Map.of() : toCountMap(issues.countByProjectsAndTypeGrouped(pids, "CR"));
        Map<Long,Long> totalBugs    = pids.isEmpty() ? Map.of() : toCountMap(issues.countByProjectsAndTypeGrouped(pids, "PROD_BUG"));
        // Non-open-filtered — matches the accounts/exceptions rows' historic semantics.
        Map<Long,Long> slaBreached  = pids.isEmpty() ? Map.of() : toCountMap(issues.countByProjectsTypeAndSlaStatusGrouped(pids, "PROD_BUG", "Breached"));

        // Open-filtered portfolio totals — matches the kpis historic semantics.
        long slaBreachedOpen = pids.isEmpty() ? 0 : issues.countOpenBugsBySlaStatusForProjects(pids, "Breached");
        long slaAtRiskOpen   = pids.isEmpty() ? 0 : issues.countOpenBugsBySlaStatusForProjects(pids, "At risk");

        Map<String,Long> sevMap = new HashMap<>();
        if (!pids.isEmpty()) {
            issues.countOpenProdBugsBySeverityForProjects(pids)
                .forEach(r -> sevMap.put(String.valueOf(r[0]), ((Number) r[1]).longValue()));
        }

        ProjectHealthService.HealthContext hctx = health.preloadContext(projs);
        Map<Long,ProjectHealthService.HealthResult> healthByProject = new HashMap<>();
        for (Project p : projs) healthByProject.put(p.getId(), health.compute(p, hctx));

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("summary",    buildSummary(portfolio, projs, totalCrs, totalBugs, healthByProject));
        out.put("kpis",       buildKpis(projs, openCrs, openBugs, sevMap, slaBreachedOpen, slaAtRiskOpen, healthByProject));
        out.put("accounts",   buildAccounts(projs, openCrs, openBugs, slaBreached, healthByProject));
        out.put("exceptions", buildExceptions(portfolio, projs, totalCrs, totalBugs, slaBreached));
        return out;
    }

    private Map<String,Object> buildSummary(Portfolio portfolio, List<Project> projs,
                                            Map<Long,Long> totalCrs, Map<Long,Long> totalBugs,
                                            Map<Long,ProjectHealthService.HealthResult> healthByProject) {
        long crs  = projs.stream().mapToLong(p -> totalCrs.getOrDefault(p.getId(), 0L)).sum();
        long bugs = projs.stream().mapToLong(p -> totalBugs.getOrDefault(p.getId(), 0L)).sum();

        int atRiskAccounts = 0;
        BigDecimal revenueManaged = BigDecimal.ZERO;
        for (Project p : projs) {
            int amber = p.getHealthAmberThreshold() != null ? p.getHealthAmberThreshold() : 60;
            if (healthByProject.get(p.getId()).healthPct() < amber) atRiskAccounts++;
            if (p.getRevenueExposure() != null) revenueManaged = revenueManaged.add(p.getRevenueExposure());
        }

        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",             portfolio.getId());
        m.put("name",           portfolio.getName());
        m.put("projectCount",   projs.size());
        m.put("totalCrs",       crs);
        m.put("openBugs",       bugs);
        m.put("atRiskAccounts", atRiskAccounts);
        m.put("revenueManaged", revenueManaged);
        return m;
    }

    private Map<String,Object> buildKpis(List<Project> projs,
                                         Map<Long,Long> openCrs, Map<Long,Long> openBugs,
                                         Map<String,Long> sevMap, long slaBreachedOpen, long slaAtRiskOpen,
                                         Map<Long,ProjectHealthService.HealthResult> healthByProject) {
        long crs  = projs.stream().mapToLong(p -> openCrs.getOrDefault(p.getId(), 0L)).sum();
        long bugs = projs.stream().mapToLong(p -> openBugs.getOrDefault(p.getId(), 0L)).sum();

        int healthPct = projs.isEmpty() ? 100
            : (int) Math.round(projs.stream()
                .mapToInt(p -> healthByProject.get(p.getId()).healthPct()).average().orElse(100));

        // Revenue at risk: SUM revenue_exposure of projects with healthPct below amber threshold.
        BigDecimal revenueAtRisk = BigDecimal.ZERO;
        for (Project p : projs) {
            int amber = p.getHealthAmberThreshold() != null ? p.getHealthAmberThreshold() : 60;
            if (healthByProject.get(p.getId()).healthPct() < amber && p.getRevenueExposure() != null) {
                revenueAtRisk = revenueAtRisk.add(p.getRevenueExposure());
            }
        }

        Map<String,Object> m = new LinkedHashMap<>();
        m.put("accountCount",  projs.size());
        m.put("healthPct",     healthPct);
        m.put("openCrs",       crs);
        m.put("prodBugs",      bugs);
        m.put("p0",            sevMap.getOrDefault("P0", 0L));
        m.put("p1",            sevMap.getOrDefault("P1", 0L));
        m.put("p2",            sevMap.getOrDefault("P2", 0L));
        m.put("p3",            sevMap.getOrDefault("P3", 0L));
        m.put("slaBreached",   slaBreachedOpen);
        m.put("slaAtRisk",     slaAtRiskOpen);
        m.put("revenueAtRisk", revenueAtRisk);
        return m;
    }

    private List<Map<String,Object>> buildAccounts(List<Project> projs,
                                                   Map<Long,Long> openCrs, Map<Long,Long> openBugs,
                                                   Map<Long,Long> slaBreached,
                                                   Map<Long,ProjectHealthService.HealthResult> healthByProject) {
        List<Map<String,Object>> result = new ArrayList<>();
        for (Project p : projs) {
            var hr = healthByProject.get(p.getId());
            int green = p.getHealthGreenThreshold() != null ? p.getHealthGreenThreshold() : 80;
            int amber = p.getHealthAmberThreshold() != null ? p.getHealthAmberThreshold() : 60;
            String rag = hr.healthPct() < amber ? "Red" : hr.healthPct() < green ? "Amber" : "Green";
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",          p.getId());
            m.put("name",        p.getName());
            m.put("clientName",  p.getClient() != null ? p.getClient().getName() : "");
            m.put("clientId",    p.getClient() != null ? p.getClient().getId()   : null);
            m.put("openCrs",     openCrs.getOrDefault(p.getId(), 0L));
            m.put("prodBugs",    openBugs.getOrDefault(p.getId(), 0L));
            m.put("slaBreached", slaBreached.getOrDefault(p.getId(), 0L));
            m.put("healthPct",            hr.healthPct());
            m.put("healthGreenThreshold", green);
            m.put("healthAmberThreshold", amber);
            m.put("stage",                hr.stage());
            m.put("rag",                  rag);
            result.add(m);
        }
        return result;
    }

    private List<Map<String,Object>> buildExceptions(Portfolio portfolio, List<Project> projs,
                                                     Map<Long,Long> totalCrs, Map<Long,Long> totalBugs,
                                                     Map<Long,Long> slaBreachedMap) {
        List<Map<String,Object>> result = new ArrayList<>();
        for (Project p : projs) {
            String client = p.getClient() != null ? p.getClient().getName() : "Unknown";
            long bugs        = totalBugs.getOrDefault(p.getId(), 0L);
            long crs         = totalCrs.getOrDefault(p.getId(), 0L);
            long slaBreached = slaBreachedMap.getOrDefault(p.getId(), 0L);
            if (slaBreached > 0) result.add(Map.of(
                "client", client, "pod", portfolio.getName(), "risk", "SLA breached",
                "owner", "Support Manager",
                "impact", slaBreached + " prod " + (slaBreached == 1 ? "bug" : "bugs") + " SLA breached",
                "nextAction", "Hotfix ETA required today"));
            else if (bugs > 0) result.add(Map.of(
                "client", client, "pod", portfolio.getName(), "risk", "Production SLA",
                "owner", "Support Manager",
                "impact", bugs + " open prod " + (bugs == 1 ? "issue" : "issues"),
                "nextAction", "Review SLA and assign owners"));
            if (crs > 15) result.add(Map.of(
                "client", client, "pod", portfolio.getName(), "risk", "Delayed CRs",
                "owner", "PM Owner",
                "impact", crs + " CRs in scope",
                "nextAction", "Rebaseline release plan"));
        }
        return result;
    }

    private static Map<Long,Long> toCountMap(List<Object[]> rows) {
        Map<Long,Long> out = new HashMap<>();
        rows.forEach(r -> out.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue()));
        return out;
    }
}
