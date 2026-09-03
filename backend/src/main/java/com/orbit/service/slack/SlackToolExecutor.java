package com.orbit.service.slack;

import com.orbit.domain.alert.Alert;
import com.orbit.domain.capacity.Developer;
import com.orbit.domain.capacity.ManDaySnapshot;
import com.orbit.domain.client.AppUser;
import com.orbit.domain.client.Project;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.domain.report.GeneratedReport;
import com.orbit.integration.slack.SlackInteractionRouter.Surface;
import com.orbit.integration.slack.SlackResponseRenderer;
import com.orbit.integration.slack.SlackResponseRenderer.AlertRow;
import com.orbit.integration.slack.SlackResponseRenderer.BriefingLine;
import com.orbit.integration.slack.SlackResponseRenderer.CapacityRow;
import com.orbit.integration.slack.SlackResponseRenderer.CrRow;
import com.orbit.integration.slack.SlackResponseRenderer.ReportRow;
import com.orbit.repository.AlertRepository;
import com.orbit.repository.DeveloperRepository;
import com.orbit.repository.GeneratedReportRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.ManDaySnapshotRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.service.agent.AgentInvocationResult;
import com.orbit.service.agent.AgentInvocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Executes a {@link IntentResolver.ResolvedIntent} against the live read-only repositories
 * or kicks off a native agent via {@link AgentInvocationService} (for orbit.run_* tools)
 * and returns a Block Kit payload ready for {@link com.orbit.integration.slack.SlackClient}.
 *
 * Phase 2.2: run_* tools synchronously invoke the agent and render a result card.
 * Phase 2.3 will swap this for placeholder-post → chat.update with the result.
 */
@Service
public class SlackToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(SlackToolExecutor.class);

    private final AlertRepository alerts;
    private final JiraIssueRepository jira;
    private final SlackResponseRenderer renderer;
    private final AgentInvocationService invocations;
    private final ProjectRepository projects;
    private final DeveloperRepository developers;
    private final ManDaySnapshotRepository manDaySnapshots;
    private final GeneratedReportRepository reports;

    public SlackToolExecutor(AlertRepository alerts,
                             JiraIssueRepository jira,
                             SlackResponseRenderer renderer,
                             AgentInvocationService invocations,
                             ProjectRepository projects,
                             DeveloperRepository developers,
                             ManDaySnapshotRepository manDaySnapshots,
                             GeneratedReportRepository reports) {
        this.alerts = alerts;
        this.jira = jira;
        this.renderer = renderer;
        this.invocations = invocations;
        this.projects = projects;
        this.developers = developers;
        this.manDaySnapshots = manDaySnapshots;
        this.reports = reports;
    }

    /**
     * Suggests follow-up actions for the tool that just ran. Each suggestion carries the
     * minimal args needed to make it self-contained; {@link com.orbit.integration.slack.SlackInteractionRouter}
     * folds them with the thread context on dispatch.
     */
    public List<SlackResponseRenderer.Suggestion> suggestionsFor(IntentResolver.ResolvedIntent intent) {
        if (intent == null) return List.of();
        String project = stringArg(intent.args(), "projectName");
        String projectArgs = project == null ? "{}" : "{\"projectName\":\"" + project.replace("\"", "\\\"") + "\"}";
        return switch (intent.tool()) {
            case "orbit.get_alerts" -> List.of(
                new SlackResponseRenderer.Suggestion("Show P0 bugs", "orbit.get_bugs", "{\"severity\":\"P0\"}"),
                new SlackResponseRenderer.Suggestion("Today's briefing", "orbit.get_briefing", "{}")
            );
            case "orbit.get_bugs" -> List.of(
                new SlackResponseRenderer.Suggestion("Critical alerts", "orbit.get_alerts", "{\"severity\":\"critical\"}"),
                new SlackResponseRenderer.Suggestion("CRs in flight", "orbit.get_crs", projectArgs)
            );
            case "orbit.get_briefing" -> List.of(
                new SlackResponseRenderer.Suggestion("Critical alerts", "orbit.get_alerts", "{\"severity\":\"critical\"}"),
                new SlackResponseRenderer.Suggestion("Capacity", "orbit.get_capacity", "{}")
            );
            case "orbit.run_forecast" -> List.of(
                new SlackResponseRenderer.Suggestion("Run delivery briefing", "orbit.run_briefing", "{}")
            );
            case "orbit.run_briefing" -> List.of(
                new SlackResponseRenderer.Suggestion("Critical alerts", "orbit.get_alerts", "{\"severity\":\"critical\"}")
            );
            default -> List.of();
        };
    }

    /** Builds the Block Kit blocks. Returns an empty-state card if the tool isn't wired yet. */
    public List<Map<String, Object>> execute(IntentResolver.ResolvedIntent intent, AppUser user, Surface surface) {
        if (intent == null) return renderer.emptyState("Orbit", "I didn't understand that. Try `alerts critical` or `bugs p0`.");
        return switch (intent.tool()) {
            case "orbit.get_alerts"  -> alerts(intent.args());
            case "orbit.get_bugs"    -> bugs(intent.args());
            case "orbit.run_report"    -> runReport(user, intent.args(), surface);
            case "orbit.run_forecast"  -> runAgent("forecast.manday", "Forecast", user, intent.args(), surface);
            case "orbit.run_briefing"  -> runAgent("briefing.delivery", "Delivery briefing", user, intent.args(), surface);
            case "orbit.get_crs"           -> crs(intent.args());
            case "orbit.get_briefing"      -> briefing();
            case "orbit.get_forecast"      -> forecast(intent.args());
            case "orbit.get_capacity"      -> capacity();
            case "orbit.get_report_status" -> reportStatus();
            default                        -> renderer.emptyState("Orbit", "Unknown tool: " + intent.tool());
        };
    }

    /** Back-compat / no-context overload (used by tests and legacy callers). */
    public List<Map<String, Object>> execute(IntentResolver.ResolvedIntent intent) {
        return execute(intent, null, Surface.SLASH);
    }

    /** Short fallback text shown by Slack when blocks can't render (mobile notifications, etc.). */
    public String fallbackText(IntentResolver.ResolvedIntent intent) {
        if (intent == null) return "Orbit didn't understand that.";
        return "Orbit · " + displayName(intent.tool());
    }

    // ── read-only tools ──────────────────────────────────────────────────────

    private List<Map<String, Object>> alerts(Map<String, Object> args) {
        String severity = stringArg(args, "severity");
        String normSev = severity == null ? null : severity.toUpperCase();
        var page = alerts.findFiltered(normSev, "OPEN", null, PageRequest.of(0, 10));
        List<AlertRow> rows = page.getContent().stream()
            .map(a -> new AlertRow(
                a.getSeverity(),
                a.getTitle(),
                a.getProject() != null ? a.getProject().getName() : null,
                relativeTime(a)))
            .toList();
        return renderer.alerts(rows);
    }

    private List<Map<String, Object>> bugs(Map<String, Object> args) {
        String severity = stringArg(args, "severity");
        var page = jira.findProdBugs(null, severity, null, PageRequest.of(0, 50));
        int p0 = 0, p1 = 0, p2 = 0, p3 = 0;
        for (var bug : page.getContent()) {
            String s = bug.getSeverity();
            if      ("P0".equals(s)) p0++;
            else if ("P1".equals(s)) p1++;
            else if ("P2".equals(s)) p2++;
            else if ("P3".equals(s)) p3++;
        }
        long breached = page.getContent().stream()
            .filter(b -> "Breached".equalsIgnoreCase(b.getSlaStatus()))
            .count();
        return renderer.bugSummary(p0, p1, p2, p3, (int) breached);
    }

    private List<Map<String, Object>> crs(Map<String, Object> args) {
        String projectName = stringArg(args, "projectName");
        var pageable = PageRequest.of(0, 10);
        List<JiraIssue> issues;
        if (projectName != null && !projectName.isBlank()) {
            List<Long> projectIds = findProjectIdsByName(projectName);
            if (projectIds.isEmpty()) {
                return renderer.emptyState("CRs", "No project matches `" + projectName + "`.");
            }
            issues = jira.findCrsByProjectIds(projectIds, null, null, pageable).getContent();
        } else {
            issues = jira.findCrs(null, null, null, pageable).getContent();
        }
        List<CrRow> rows = issues.stream().map(j -> new CrRow(
            j.getIssueKey(),
            j.getSummary(),
            j.getLifecycleStage(),
            j.getPriority(),
            j.getAssigneeName(),
            relativeAge(j.getCreatedAt())
        )).toList();
        return renderer.crs(rows);
    }

    private List<Map<String, Object>> briefing() {
        long critical = alerts.findFiltered("CRITICAL", "OPEN", null, PageRequest.of(0, 1)).getTotalElements();
        long warning  = alerts.findFiltered("WARNING",  "OPEN", null, PageRequest.of(0, 1)).getTotalElements();
        long p0Bugs   = jira.findProdBugs(null, "P0", null, PageRequest.of(0, 1)).getTotalElements();
        long openCrs  = jira.findCrs(null, null, null, PageRequest.of(0, 1)).getTotalElements();

        List<BriefingLine> lines = new java.util.ArrayList<>();
        if (critical > 0) lines.add(new BriefingLine("critical", critical + " critical alert" + plural(critical) + " open"));
        if (p0Bugs > 0)   lines.add(new BriefingLine("critical", p0Bugs + " P0 bug" + plural(p0Bugs) + " in flight"));
        if (warning > 0)  lines.add(new BriefingLine("watch", warning + " warning alert" + plural(warning) + " open"));
        if (openCrs > 0)  lines.add(new BriefingLine("info", openCrs + " open change request" + plural(openCrs)));
        if (lines.isEmpty()) lines.add(new BriefingLine("healthy", "All quiet — no critical signals."));
        return renderer.briefing(lines);
    }

    private List<Map<String, Object>> forecast(Map<String, Object> args) {
        String projectName = stringArg(args, "projectName");
        if (projectName == null || projectName.isBlank()) {
            return renderer.emptyState("Forecast", "Tell me which project, e.g. `forecast Apollo`.");
        }
        Optional<Project> match = projects.findByActiveTrue().stream()
            .filter(p -> projectName.equalsIgnoreCase(p.getName()))
            .findFirst();
        if (match.isEmpty()) {
            return renderer.emptyState("Forecast", "No project matches `" + projectName + "`.");
        }
        Project project = match.get();
        List<ManDaySnapshot> snaps = manDaySnapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(project.getId());
        if (snaps.isEmpty()) {
            return renderer.forecast(project.getName(), null, null, null, null);
        }
        ManDaySnapshot latest = snaps.get(0);
        BigDecimal burned    = latest.getBurnedDays();
        BigDecimal remaining = latest.getRemainingDays();
        BigDecimal rate      = latest.getBurnRatePerDay();
        Integer burnPct = null;
        if (burned != null && remaining != null) {
            BigDecimal total = burned.add(remaining);
            if (total.signum() > 0) {
                burnPct = burned.multiply(BigDecimal.valueOf(100)).divide(total, 0, java.math.RoundingMode.HALF_UP).intValue();
            }
        }
        Integer daysToExhaust = null;
        if (remaining != null && rate != null && rate.signum() > 0) {
            daysToExhaust = remaining.divide(rate, 0, java.math.RoundingMode.HALF_UP).intValue();
        }
        String exhDate = latest.getForecastExhaustion() == null ? null
            : latest.getForecastExhaustion().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String rateStr = rate == null ? null : rate.toPlainString();
        return renderer.forecast(project.getName(), burnPct, rateStr, exhDate, daysToExhaust);
    }

    private List<Map<String, Object>> capacity() {
        List<Developer> devs = developers.findAllByOrderByUtilizationDesc();
        if (devs.isEmpty()) return renderer.emptyState("Capacity", "No developers configured.");
        Map<String, int[]> byTeam = new HashMap<>();
        for (Developer d : devs) {
            String team = d.getTeam() == null || d.getTeam().isBlank() ? "—" : d.getTeam();
            int util = d.getUtilization() == null ? 0 : d.getUtilization();
            int[] agg = byTeam.computeIfAbsent(team, k -> new int[]{0, 0});
            agg[0] += util;
            agg[1] += 1;
        }
        List<CapacityRow> rows = byTeam.entrySet().stream()
            .map(e -> new CapacityRow(e.getKey(), e.getValue()[1] == 0 ? 0 : e.getValue()[0] / e.getValue()[1], e.getValue()[1]))
            .sorted((a, b) -> Integer.compare(b.utilisationPct(), a.utilisationPct()))
            .toList();
        return renderer.capacity(rows);
    }

    private List<Map<String, Object>> reportStatus() {
        var page = reports.findFiltered(null, PageRequest.of(0, 5));
        List<ReportRow> rows = page.getContent().stream().map(r -> new ReportRow(
            r.getType(),
            r.getStatus(),
            r.getClient() == null ? null : r.getClient().getName(),
            r.getGeneratedBy(),
            r.getGeneratedAt() == null ? null : r.getGeneratedAt().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        )).toList();
        return renderer.reportStatus(rows);
    }

    private List<Long> findProjectIdsByName(String projectName) {
        return projects.findByActiveTrue().stream()
            .filter(p -> projectName.equalsIgnoreCase(p.getName()))
            .map(Project::getId)
            .toList();
    }

    private static String plural(long n) { return n == 1 ? "" : "s"; }

    private static String relativeAge(java.time.LocalDateTime ts) {
        if (ts == null) return null;
        long sec = java.time.Duration.between(ts, java.time.LocalDateTime.now()).getSeconds();
        if (sec < 60)        return sec + "s ago";
        if (sec < 3600)      return (sec / 60) + "m ago";
        if (sec < 86_400)    return (sec / 3600) + "h ago";
        return (sec / 86_400) + "d ago";
    }

    // ── invocation tools ─────────────────────────────────────────────────────

    /**
     * report.draft needs a {@code reportId} (the row was created up-front from the UI).
     * Slack users typically don't know the id, so we accept it via args when supplied
     * and otherwise render an explainer. Future work: surface a "create + draft" path.
     */
    private List<Map<String, Object>> runReport(AppUser user, Map<String, Object> args, Surface surface) {
        if (!args.containsKey("reportId")) {
            return renderer.emptyState("Report draft",
                "Create the report from the Orbit UI first, then I can draft it. "
                + "Or run `/orbit run report reportId:<n>` once you have one.");
        }
        return runAgent("report.draft", "Report draft", user, args, surface);
    }

    private List<Map<String, Object>> runAgent(String agentKey, String title,
                                               AppUser user, Map<String, Object> args, Surface surface) {
        String source = invocationSource(surface);
        try {
            AgentInvocationResult result = invocations.invoke(user, agentKey, args, source);
            String message = "COMPLETED".equals(result.status())
                ? ":white_check_mark: Run #" + result.runId() + " started · " + safeSummary(result.summary())
                : ":x: Run #" + result.runId() + " failed · " + safeSummary(result.summary());
            return renderer.emptyState(title, message);
        } catch (RuntimeException e) {
            log.warn("Slack invocation of {} failed: {}", agentKey, e.getMessage());
            return renderer.emptyState(title, ":x: Couldn't kick off " + agentKey + ": " + e.getMessage());
        }
    }

    static String invocationSource(Surface surface) {
        if (surface == null) return "SLACK_SLASH";
        return switch (surface) {
            case SLASH   -> "SLACK_SLASH";
            case MENTION -> "SLACK_MENTION";
            case DM      -> "SLACK_DM";
        };
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String safeSummary(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 197) + "..." : s;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object v = args.get(key);
        return v == null ? null : v.toString();
    }

    private static String relativeTime(Alert a) {
        if (a.getCreatedAt() == null) return null;
        long sec = java.time.Duration.between(a.getCreatedAt(), java.time.LocalDateTime.now()).getSeconds();
        if (sec < 60)        return sec + "s ago";
        if (sec < 3600)      return (sec / 60) + "m ago";
        if (sec < 86_400)    return (sec / 3600) + "h ago";
        return (sec / 86_400) + "d ago";
    }

    private static String displayName(String tool) {
        return switch (tool) {
            case "orbit.get_alerts"        -> "Alerts";
            case "orbit.get_bugs"          -> "Bug summary";
            case "orbit.get_crs"           -> "CRs";
            case "orbit.get_briefing"      -> "Daily briefing";
            case "orbit.get_forecast"      -> "Forecast";
            case "orbit.get_capacity"      -> "Capacity";
            case "orbit.get_report_status" -> "Report status";
            case "orbit.run_report"        -> "Report draft";
            case "orbit.run_forecast"      -> "Forecast run";
            case "orbit.run_briefing"      -> "Delivery briefing run";
            default                        -> tool;
        };
    }
}
