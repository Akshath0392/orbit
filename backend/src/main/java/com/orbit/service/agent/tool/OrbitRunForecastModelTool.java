package com.orbit.service.agent.tool;

import com.orbit.repository.ManDayBudgetRepository;
import com.orbit.repository.ManDaySnapshotRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrbitRunForecastModelTool implements AgentTool {

    private final ManDaySnapshotRepository snapshots;
    private final ManDayBudgetRepository budgets;

    public OrbitRunForecastModelTool(ManDaySnapshotRepository snapshots, ManDayBudgetRepository budgets) {
        this.snapshots = snapshots;
        this.budgets = budgets;
    }

    @Override public String id()            { return "orbit.run_forecast_model"; }
    @Override public String description()   { return "Run linear burn-rate forecast for a project (30-day horizon)"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        Long projectId = ctx != null ? ctx.getProjectId() : null;
        if (args.containsKey("projectId")) {
            try { projectId = Long.parseLong(String.valueOf(args.get("projectId"))); } catch (Exception ignored) {}
        }
        if (projectId == null) return Map.of("error", "projectId_required");

        final Long pid = projectId;
        List<com.orbit.domain.capacity.ManDaySnapshot> history =
            snapshots.findByProjectIdOrderBySnapshotDateDesc(pid);

        if (history.size() < 2) {
            return Map.of("error", "insufficient_history", "snapshotCount", history.size(),
                "note", "need at least 2 snapshots to compute trend");
        }

        List<Double> yVals = history.stream()
            .map(s -> s.getBurnedDays() != null ? s.getBurnedDays().doubleValue() : 0.0)
            .collect(Collectors.toList());
        int n = yVals.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i; sumY += yVals.get(i); sumXY += i * yVals.get(i); sumX2 += (double) i * i;
        }
        double slope = (n * sumXY - sumX * sumY) / Math.max(1, n * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;

        List<Map<String, Object>> forecast = new ArrayList<>();
        LocalDate base = LocalDate.now();
        for (int d = 1; d <= 30; d++) {
            double yhat = intercept + slope * (n - 1 + d);
            forecast.add(Map.of(
                "ds", base.plusDays(d).toString(),
                "yhat", Math.round(yhat * 100.0) / 100.0
            ));
        }

        double exhaustionDays = slope > 0
            ? budgets.findByProjectId(pid)
                .map(b -> b.getPurchasedDays() != null
                    ? (b.getPurchasedDays().doubleValue() - intercept) / slope - (n - 1)
                    : Double.NaN)
                .orElse(Double.NaN)
            : Double.NaN;

        String exhaustionDate = Double.isNaN(exhaustionDays) || exhaustionDays < 0 ? "unknown"
            : base.plusDays((long) exhaustionDays).toString();

        return Map.of(
            "projectId", pid,
            "forecast", forecast,
            "burnRatePerDay", Math.round(slope * 100.0) / 100.0,
            "forecastExhaustionDate", exhaustionDate,
            "model", "linear_regression",
            "snapshotCount", n
        );
    }
}
