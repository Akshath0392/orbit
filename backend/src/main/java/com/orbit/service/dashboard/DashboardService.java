package com.orbit.service.dashboard;

import com.orbit.domain.alert.Alert;
import com.orbit.domain.client.Project;
import com.orbit.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.orbit.service.ai.AiGatewayService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final ProjectRepository projects;
    private final AlertRepository alerts;
    private final ManDaySnapshotRepository snapshots;
    private final DeveloperRepository developers;
    private final RiskScoringService riskScoring;
    private final AiGatewayService ai;
    private final AppUserRepository appUsers;

    // Simple in-memory cache for the AI briefing — TTL 2h (per HLD §8)
    private volatile String cachedBriefingJson = null;
    private volatile LocalDateTime briefingCachedAt = null;
    private static final int BRIEFING_TTL_MINUTES = 120;

    public DashboardService(ProjectRepository projects, AlertRepository alerts,
                             ManDaySnapshotRepository snapshots, DeveloperRepository developers,
                             RiskScoringService riskScoring, AiGatewayService ai,
                             AppUserRepository appUsers) {
        this.projects = projects; this.alerts = alerts;
        this.snapshots = snapshots; this.developers = developers;
        this.riskScoring = riskScoring; this.ai = ai;
        this.appUsers = appUsers;
    }

    @org.springframework.cache.annotation.Cacheable(com.orbit.config.CacheConfig.RADAR)
    public Map<String, Object> getRadar() {
        log.debug("Building radar — scoring all active projects");
        // ── 1. Score every active project from live DB data ───────────────
        // Bulk-preload all scoring inputs: fixed query count regardless of
        // project count. The developer list doubles as the team-capacity
        // source below.
        List<Project> activeProjects = projects.findActiveWithClientAndPortfolio();
        var devList = developers.findAllByOrderByUtilizationDesc();
        RiskScoringService.RiskContext riskCtx = riskScoring.preload(activeProjects, devList);
        List<Map<String,Object>> projectCards = activeProjects.stream()
            .map(p -> buildProjectCard(p, riskCtx))
            .collect(Collectors.toList());

        // ── 2. Alert summary ──────────────────────────────────────────────
        long critical = alerts.countBySeverityAndStatus("critical", "OPEN");
        long risk     = alerts.countBySeverityAndStatus("risk",     "OPEN");
        long info     = alerts.countBySeverityAndStatus("info",     "OPEN");

        // Compute stat card values from live project cards
        long projectsAtRisk   = projectCards.stream().filter(c -> "critical".equals(c.get("risk")) || "watch".equals(c.get("risk"))).count();
        long budgetAlerts     = projectCards.stream().filter(c -> c.get("burn") instanceof Number n && n.intValue() > 80).count();

        Map<String,Object> alertSummary = new LinkedHashMap<>();
        alertSummary.put("critical",             critical);
        alertSummary.put("risk",                 risk);
        alertSummary.put("info",                 info);
        alertSummary.put("projectsAtRisk",        projectsAtRisk);
        alertSummary.put("projectsAtRiskSub",     critical + " critical · " + risk + " at risk");
        alertSummary.put("deliveriesAtRisk",      risk);
        alertSummary.put("deliveriesAtRiskSub",   "open risk alerts");
        alertSummary.put("budgetAlerts",          budgetAlerts);
        alertSummary.put("budgetAlertsSub",       "projects burn >80%");

        // ── 3. Team capacity ──────────────────────────────────────────────
        long overloaded = devList.stream().filter(d -> d.getUtilization() != null && d.getUtilization() >= 85).count();
        long busy       = devList.stream().filter(d -> d.getUtilization() != null && d.getUtilization() >= 70 && d.getUtilization() < 85).count();
        long onLeave    = devList.stream().filter(d -> Boolean.TRUE.equals(d.getOnLeave())).count();
        long available  = devList.size() - overloaded - busy - onLeave;

        alertSummary.put("teamOverload",          overloaded);
        alertSummary.put("teamOverloadSub",        overloaded + " devs >" + 85 + "% util");

        Map<String,Object> teamCapacity = new LinkedHashMap<>();
        teamCapacity.put("overloaded", overloaded);
        teamCapacity.put("busy",       busy);
        teamCapacity.put("available",  available);
        teamCapacity.put("onLeave",    onLeave);

        // ── 4. AI briefing (cached 2h, generated from live scores) ───────
        Map<String,Object> aiBriefing = buildAiBriefing(projectCards, critical, risk);

        // ── 5. Sync health ────────────────────────────────────────────────
        Map<String,Object> syncHealth = new LinkedHashMap<>();
        syncHealth.put("lastSyncedAt", LocalDateTime.now().minusMinutes(4).toString());
        syncHealth.put("status",       "OK");

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("projects",     projectCards);
        result.put("alertSummary", alertSummary);
        result.put("teamCapacity", teamCapacity);
        result.put("syncHealth",   syncHealth);
        result.put("aiBriefing",   aiBriefing);
        return result;
    }

    // ── Build one project card using RiskScoringService ───────────────────

    private Map<String,Object> buildProjectCard(Project p, RiskScoringService.RiskContext ctx) {
        RiskScoringService.ProjectRiskResult r = riskScoring.score(p, ctx);

        Map<String,Object> card = new LinkedHashMap<>();
        card.put("id",          p.getId());
        card.put("name",        p.getName());
        card.put("client",      p.getClient() != null ? p.getClient().getName() : "");
        card.put("portfolio",   p.getPortfolio() != null ? p.getPortfolio().getName() : "");
        card.put("risk",        r.riskLevel());
        card.put("prob",        r.slipProbabilityPct());
        card.put("burn",        r.burnPct());
        card.put("load",        r.loadPct());
        card.put("exhaustion",  r.forecastExhaustionDate());
        card.put("signals",     r.signals());
        card.put("heat",        r.heat());
        card.put("insight",     r.insight());
        card.put("rawScore",    r.rawScore());
        return card;
    }

    // ── AI Briefing: generate from live scores, cache for 2h ─────────────

    @SuppressWarnings("unchecked")
    private Map<String,Object> buildAiBriefing(List<Map<String,Object>> cards,
                                                long criticalCount, long riskCount) {
        // Return cached version if still fresh
        if (cachedBriefingJson != null && briefingCachedAt != null
                && briefingCachedAt.plusMinutes(BRIEFING_TTL_MINUTES).isAfter(LocalDateTime.now())) {
            return parseCachedBriefing();
        }

        // Build context from live scores
        StringBuilder ctx = new StringBuilder();
        ctx.append("Today: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))).append("\n");
        ctx.append("Open critical alerts: ").append(criticalCount).append(", risk alerts: ").append(riskCount).append("\n\n");
        ctx.append("Project risk summary:\n");
        for (Map<String,Object> card : cards) {
            ctx.append("- ").append(card.get("name")).append(" (").append(card.get("client")).append("): ")
               .append(card.get("risk")).append(" risk, ").append(card.get("prob")).append("% slip, ")
               .append(card.get("burn")).append("% burn. Signals: ").append(card.get("signals")).append("\n");
        }

        // Call LLM to generate 4 bullet points
        String systemPrompt = """
            You are an AI delivery intelligence assistant for a software engineering team.
            Generate exactly 4 concise executive briefing bullets from the project data provided.
            Each bullet must be 1 sentence. Use these severity levels: critical, watch, healthy.
            Format each line as: [LEVEL] Text.
            Example: [critical] Sigma Telecom budget exhausts in 8 days — P0 SLA breached.
            Focus on the most urgent actionable facts. Be specific about project names and timelines.
            """;

        String response = ai.complete(systemPrompt, ctx.toString());

        // Parse LLM response into structured bullets
        List<Map<String,Object>> bullets = parseBullets(response, cards);

        // Cache result
        Map<String,Object> briefing = new LinkedHashMap<>();
        briefing.put("generatedAt",  LocalDateTime.now().toString());
        briefing.put("confidence",   criticalCount >= 2 ? "HIGH" : criticalCount >= 1 ? "MEDIUM" : "LOW");
        briefing.put("agentLabel",   "DeliveryIntelligenceAgent");
        briefing.put("signalCount",  cards.size() * 3 + (int)(criticalCount * 2));
        briefing.put("bullets",      bullets);
        cachedBriefingJson   = bulletsToJson(bullets);
        briefingCachedAt     = LocalDateTime.now();

        return briefing;
    }

    /** Parse "[critical] Text" or "[watch] Text" lines from LLM response */
    private List<Map<String,Object>> parseBullets(String response, List<Map<String,Object>> fallbackCards) {
        List<Map<String,Object>> bullets = new ArrayList<>();
        for (String line : response.split("\n")) {
            line = line.trim();
            if (line.isBlank()) continue;
            String level = "info";
            String text  = line;
            if (line.toLowerCase().startsWith("[critical]")) {
                level = "critical"; text = line.substring(10).trim();
            } else if (line.toLowerCase().startsWith("[watch]")) {
                level = "watch"; text = line.substring(7).trim();
            } else if (line.toLowerCase().startsWith("[healthy]")) {
                level = "healthy"; text = line.substring(9).trim();
            } else if (line.toLowerCase().startsWith("[info]")) {
                level = "info"; text = line.substring(6).trim();
            }
            if (!text.isBlank()) {
                Map<String,Object> b = new LinkedHashMap<>();
                b.put("level", level); b.put("text", text);
                bullets.add(b);
            }
            if (bullets.size() == 5) break;
        }
        // Fallback: generate bullets from project cards if LLM didn't produce enough
        if (bullets.size() < 2) {
            bullets.clear();
            for (Map<String,Object> card : fallbackCards) {
                Map<String,Object> b = new LinkedHashMap<>();
                b.put("level", card.get("risk"));
                b.put("text",  card.get("name") + " (" + card.get("client") + "): " + card.get("insight"));
                bullets.add(b);
                if (bullets.size() == 5) break;
            }
        }
        return bullets;
    }

    private String bulletsToJson(List<Map<String,Object>> bullets) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bullets.size(); i++) {
            Map<String,Object> b = bullets.get(i);
            sb.append("{\"level\":\"").append(b.get("level")).append("\",\"text\":\"")
              .append(String.valueOf(b.get("text")).replace("\"","'")).append("\"}");
            if (i < bullets.size() - 1) sb.append(",");
        }
        return sb.append("]").toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> parseCachedBriefing() {
        Map<String,Object> briefing = new LinkedHashMap<>();
        briefing.put("generatedAt", briefingCachedAt.toString());
        briefing.put("confidence",  "HIGH");
        briefing.put("agentLabel",  "DeliveryIntelligenceAgent");
        briefing.put("signalCount", 0);
        // Re-parse bullets from cached JSON string
        List<Map<String,Object>> bullets = new ArrayList<>();
        String json = cachedBriefingJson.replaceAll("[\\[\\]]", "");
        for (String entry : json.split("\\},\\{")) {
            entry = entry.replace("{","").replace("}","");
            Map<String,Object> b = new LinkedHashMap<>();
            String[] parts = entry.split(",\"text\":");
            if (parts.length == 2) {
                b.put("level", parts[0].replace("\"level\":","").replace("\"","").trim());
                b.put("text",  parts[1].replace("\"","").trim());
                bullets.add(b);
            }
        }
        briefing.put("bullets", bullets);
        return briefing;
    }

    // ── Cockpit ───────────────────────────────────────────────────────────

    public Map<String,Object> getCockpit(Long userId) {
        // Pull real top-5 open alerts from DB
        List<Alert> topAlerts = alerts.findTop5ByStatusOrderByCreatedAtDesc("OPEN");
        List<Map<String,Object>> actions = topAlerts.stream().map(a -> {
            Map<String,Object> action = new LinkedHashMap<>();
            action.put("id",       a.getId());
            action.put("severity", a.getSeverity());
            action.put("tag",      a.getAlertType());
            action.put("title",    a.getTitle());
            action.put("body",     a.getDetail() != null ? a.getDetail() : "");
            action.put("client",   a.getClient() != null ? a.getClient().getName() : "All");
            action.put("time",     relativeTime(a.getCreatedAt()));
            action.put("agent",    a.getSourceAgent() != null ? a.getSourceAgent() : "System");
            return action;
        }).collect(Collectors.toList());

        // Dynamic greeting based on time of day and user lookup
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        String timeGreeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        String firstName = "there";
        if (userId != null) {
            firstName = appUsers.findById(userId)
                .map(u -> {
                    String n = u.getName();
                    if (n != null && !n.isBlank()) {
                        return n.split(" ")[0];
                    }
                    return "there";
                }).orElse("there");
        }
        String greeting = timeGreeting + ", " + firstName;

        // Dynamic date: "Monday, Jun 15" format
        String date = now.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH));

        // Dynamic autoPostAt: today at 08:30
        String autoPostAt = LocalDate.now() + "T08:30:00";

        Map<String,Object> standupDraft = new LinkedHashMap<>();
        standupDraft.put("lines", List.of(
            "Reviewing open CRs and alerts for today.",
            "Following up on pending milestones and team capacity.",
            "No blockers identified — standup ready to post."
        ));
        standupDraft.put("autoPostAt",           autoPostAt);
        standupDraft.put("secondsUntilAutoPost",  1800);

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("greeting",         greeting);
        result.put("date",             date);
        result.put("actions",          actions);
        result.put("standupDraft",     standupDraft);
        result.put("pendingProposals", List.of());
        return result;
    }

    private String relativeTime(LocalDateTime dt) {
        if (dt == null) return "just now";
        long mins = java.time.Duration.between(dt, LocalDateTime.now()).toMinutes();
        if (mins < 60) return mins + "m ago";
        long hrs = mins / 60;
        if (hrs < 24) return hrs + "h ago";
        return (hrs / 24) + "d ago";
    }
}
