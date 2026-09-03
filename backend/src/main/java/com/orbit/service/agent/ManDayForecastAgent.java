package com.orbit.service.agent;

import com.orbit.domain.agent.AgentDecisionLog;
import com.orbit.domain.capacity.ManDaySnapshot;
import com.orbit.domain.client.ManDayBudget;
import com.orbit.domain.client.Project;
import com.orbit.repository.AgentDecisionLogRepository;
import com.orbit.repository.ManDayBudgetRepository;
import com.orbit.repository.ManDaySnapshotRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.service.ai.AiGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Service
public class ManDayForecastAgent {

    private static final Logger log = LoggerFactory.getLogger(ManDayForecastAgent.class);

    private static final String AGENT_NAME = "ManDayForecastAgent";
    private static final String ALERTS_TOPIC = "/topic/alerts/budget";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AiGateway ai;
    private final SimpMessagingTemplate ws;
    private final ProjectRepository projectRepository;
    private final ManDaySnapshotRepository snapshotRepository;
    private final ManDayBudgetRepository budgetRepository;
    private final AgentDecisionLogRepository decisionLogRepository;

    public ManDayForecastAgent(AiGateway ai,
                               SimpMessagingTemplate ws,
                               ProjectRepository projectRepository,
                               ManDaySnapshotRepository snapshotRepository,
                               ManDayBudgetRepository budgetRepository,
                               AgentDecisionLogRepository decisionLogRepository) {
        this.ai = ai;
        this.ws = ws;
        this.projectRepository = projectRepository;
        this.snapshotRepository = snapshotRepository;
        this.budgetRepository = budgetRepository;
        this.decisionLogRepository = decisionLogRepository;
    }

    /**
     * Daily 8:30am forecast run across all active projects.
     */
    @Scheduled(cron = "${orbit.agents.forecast.cron:0 30 8 * * *}")
    public void runDailyForecast() {
        log.info("{}: Starting daily forecast run", AGENT_NAME);
        List<Project> activeProjects = projectRepository.findByActiveTrue();
        for (Project project : activeProjects) {
            try {
                processProject(project);
            } catch (Exception e) {
                log.error("{}: Failed to process project id={} name='{}' — {}",
                        AGENT_NAME, project.getId(), project.getName(), e.getMessage(), e);
            }
        }
        log.info("{}: Daily forecast run complete — processed {} projects", AGENT_NAME, activeProjects.size());
    }

    // -------------------------------------------------------------------------
    // Per-project forecast logic
    // -------------------------------------------------------------------------

    private void processProject(Project project) {
        Long projectId = project.getId();

        // 1. Load last 14 snapshots (repository returns them newest-first)
        List<ManDaySnapshot> snapshots =
                snapshotRepository.findTop14ByProjectIdOrderBySnapshotDateDesc(projectId);

        // Skip if fewer than 3 data points — not enough for a meaningful regression
        if (snapshots.size() < 3) {
            log.debug("{}: Skipping project id={} — only {} snapshots available (need ≥3)",
                    AGENT_NAME, projectId, snapshots.size());
            return;
        }

        // Use at most last 7 for the regression baseline (oldest first)
        List<ManDaySnapshot> regressionWindow = getRegressionWindow(snapshots);

        // 2. Linear regression on burnedDays values
        double[] yValues = extractBurnedDays(regressionWindow);
        double slope = computeSlope(yValues);
        double intercept = computeIntercept(yValues, slope);
        double stddev = computeStddev(yValues, slope, intercept);

        // 3. Produce 30-day forecast starting from tomorrow
        List<ForecastPoint> forecast = buildForecast(regressionWindow, yValues, slope, intercept, stddev, 30);

        log.debug("{}: Project '{}' — {} forecast points, slope={:.4f}",
                AGENT_NAME, project.getName(), forecast.size(), slope);

        // 4. AI interpretation of the trend
        String interpretation = interpretTrend(project, yValues, slope, forecast);

        // 5. Store decision log
        String proposalJson = buildProposalJson(project, forecast, interpretation);
        AgentDecisionLog decision = AgentDecisionLog.builder()
                .agentName(AGENT_NAME)
                .triggerEvent("Daily forecast run")
                .proposalJson(proposalJson)
                .outcome(null)
                .tokensUsed(estimateTokens(interpretation))
                .decidedAt(LocalDateTime.now())
                .build();
        decisionLogRepository.save(decision);

        // 6. Check burn threshold and emit alert WS event if needed
        checkAndEmitBurnAlert(project, yValues);
    }

    // -------------------------------------------------------------------------
    // Regression helpers
    // -------------------------------------------------------------------------

    /**
     * Returns up to the 7 oldest snapshots from the provided list (list is newest-first).
     */
    private List<ManDaySnapshot> getRegressionWindow(List<ManDaySnapshot> newestFirst) {
        int windowSize = Math.min(7, newestFirst.size());
        // Reverse to get chronological order for regression
        List<ManDaySnapshot> window = new ArrayList<>(newestFirst.subList(0, windowSize));
        java.util.Collections.reverse(window);
        return window;
    }

    private double[] extractBurnedDays(List<ManDaySnapshot> chronological) {
        double[] y = new double[chronological.size()];
        for (int i = 0; i < chronological.size(); i++) {
            ManDaySnapshot s = chronological.get(i);
            y[i] = s.getBurnedDays() != null ? s.getBurnedDays().doubleValue() : 0.0;
        }
        return y;
    }

    /**
     * Ordinary least squares slope: b1 = (n*Σxy - Σx*Σy) / (n*Σx² - (Σx)²)
     * where x = index 0..n-1.
     */
    private double computeSlope(double[] y) {
        int n = y.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += y[i];
            sumXY += i * y[i];
            sumX2 += (double) i * i;
        }
        double denom = (double) n * sumX2 - sumX * sumX;
        if (denom == 0) return 0;
        return ((double) n * sumXY - sumX * sumY) / denom;
    }

    /** Intercept: b0 = (Σy - b1*Σx) / n */
    private double computeIntercept(double[] y, double slope) {
        int n = y.length;
        double sumX = 0, sumY = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += y[i];
        }
        return (sumY - slope * sumX) / n;
    }

    /** Sample std-dev of residuals (y - yhat). */
    private double computeStddev(double[] y, double slope, double intercept) {
        int n = y.length;
        double sumSq = 0;
        for (int i = 0; i < n; i++) {
            double residual = y[i] - (slope * i + intercept);
            sumSq += residual * residual;
        }
        return n <= 1 ? 0 : Math.sqrt(sumSq / (n - 1));
    }

    /**
     * Build 30 daily forecast points.
     * The x-index for the first forecast point continues from where the regression window ends.
     */
    private List<ForecastPoint> buildForecast(List<ManDaySnapshot> window,
                                               double[] yValues,
                                               double slope,
                                               double intercept,
                                               double stddev,
                                               int days) {
        List<ForecastPoint> points = new ArrayList<>();
        int baseX = yValues.length; // next index after the last observed value

        // Determine starting date: day after the last snapshot in the window
        LocalDate lastDate = window.get(window.size() - 1).getSnapshotDate();
        if (lastDate == null) lastDate = LocalDate.now().minusDays(1);

        for (int i = 0; i < days; i++) {
            int x = baseX + i;
            double yhat = slope * x + intercept;
            LocalDate ds = lastDate.plusDays(i + 1L);

            double ci80 = stddev * 1.28;
            double ci95 = stddev * 1.96;

            points.add(new ForecastPoint(
                    ds.format(DATE_FMT),
                    round2(yhat),
                    round2(yhat - ci80),
                    round2(yhat + ci80),
                    round2(yhat - ci95),
                    round2(yhat + ci95)
            ));
        }
        return points;
    }

    // -------------------------------------------------------------------------
    // AI interpretation
    // -------------------------------------------------------------------------

    private String interpretTrend(Project project, double[] yValues, double slope, List<ForecastPoint> forecast) {
        String systemPrompt = """
                You are an AI delivery analyst. Given man-day burn data, write exactly 2 sentences:
                1. Describe the current burn trend (accelerating/flat/decelerating).
                2. State the forecast risk for the next 30 days and whether intervention is needed.
                Be specific and concise.
                """;

        StringBuilder userMessage = new StringBuilder();
        userMessage.append("Project: ").append(project.getName()).append("\n");
        userMessage.append("Last ").append(yValues.length).append(" days burned man-days: ");
        for (int i = 0; i < yValues.length; i++) {
            if (i > 0) userMessage.append(", ");
            userMessage.append(String.format("%.1f", yValues[i]));
        }
        userMessage.append("\n");
        userMessage.append("Linear regression slope: ").append(String.format("%.4f", slope)).append(" MD/day\n");
        if (!forecast.isEmpty()) {
            ForecastPoint day30 = forecast.get(forecast.size() - 1);
            userMessage.append("Forecast in 30 days: ").append(String.format("%.1f", day30.yhat()))
                       .append(" MD (95% CI: ").append(String.format("%.1f", day30.yhatLower95()))
                       .append("–").append(String.format("%.1f", day30.yhatUpper95())).append(")\n");
        }

        return ai.complete(systemPrompt, userMessage.toString());
    }

    // -------------------------------------------------------------------------
    // Burn threshold check + WS alert
    // -------------------------------------------------------------------------

    private void checkAndEmitBurnAlert(Project project, double[] latestBurnValues) {
        Optional<ManDayBudget> budgetOpt = budgetRepository.findByProjectId(project.getId());
        if (budgetOpt.isEmpty()) return;

        ManDayBudget budget = budgetOpt.get();
        if (budget.getPurchasedDays() == null || budget.getPurchasedDays().doubleValue() == 0) return;

        double currentBurned = latestBurnValues.length > 0
                ? latestBurnValues[latestBurnValues.length - 1] : 0.0;
        double burnPct = (currentBurned / budget.getPurchasedDays().doubleValue()) * 100.0;
        int threshold = budget.getAlertThresholdPct() != null ? budget.getAlertThresholdPct() : 80;

        if (burnPct > threshold) {
            log.info("{}: Project '{}' burn {}% exceeds threshold {}% — emitting WS alert",
                    AGENT_NAME, project.getName(), String.format("%.1f", burnPct), threshold);

            LinkedHashMap<String, Object> alertEvent = new LinkedHashMap<>();
            alertEvent.put("type", "budget_alert");
            alertEvent.put("projectId", project.getId());
            alertEvent.put("projectName", project.getName());
            alertEvent.put("burnPct", round2(burnPct));
            alertEvent.put("thresholdPct", threshold);
            alertEvent.put("agent", AGENT_NAME);

            ws.convertAndSend(ALERTS_TOPIC, alertEvent);
        }
    }

    // -------------------------------------------------------------------------
    // JSON / token helpers
    // -------------------------------------------------------------------------

    private String buildProposalJson(Project project, List<ForecastPoint> forecast, String interpretation) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"agent\":\"").append(AGENT_NAME).append("\",");
        sb.append("\"projectId\":").append(project.getId()).append(",");
        sb.append("\"projectName\":\"").append(escapeJson(project.getName())).append("\",");
        sb.append("\"interpretation\":\"").append(escapeJson(interpretation)).append("\",");
        sb.append("\"forecastPoints\":").append(forecast.size()).append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.split("\\s+").length * 2;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
