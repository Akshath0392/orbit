package com.orbit.service.agent.tool;

import com.orbit.repository.ManDayBudgetRepository;
import com.orbit.repository.ManDaySnapshotRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OrbitGetManDayStatusTool implements AgentTool {

    private final ManDayBudgetRepository budgets;
    private final ManDaySnapshotRepository snapshots;

    public OrbitGetManDayStatusTool(ManDayBudgetRepository budgets, ManDaySnapshotRepository snapshots) {
        this.budgets = budgets;
        this.snapshots = snapshots;
    }

    @Override public String id()            { return "orbit.get_man_day_status"; }
    @Override public String description()   { return "Current man-day burn, remaining days, and exhaustion forecast"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        Long projectId = ctx != null ? ctx.getProjectId() : null;
        if (args.containsKey("projectId")) {
            try { projectId = Long.parseLong(String.valueOf(args.get("projectId"))); } catch (Exception ignored) {}
        }
        if (projectId == null) return Map.of("error", "projectId_required");

        final Long pid = projectId;
        return budgets.findByProjectId(pid).map(budget -> {
            List<com.orbit.domain.capacity.ManDaySnapshot> snaps =
                snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(pid);

            BigDecimal burned = BigDecimal.ZERO;
            BigDecimal remaining = BigDecimal.ZERO;
            BigDecimal burnRate = BigDecimal.ZERO;
            String forecastExhaustion = "N/A";

            if (!snaps.isEmpty()) {
                com.orbit.domain.capacity.ManDaySnapshot latest = snaps.get(0);
                burned = latest.getBurnedDays() != null ? latest.getBurnedDays() : BigDecimal.ZERO;
                remaining = latest.getRemainingDays() != null ? latest.getRemainingDays() : BigDecimal.ZERO;
                burnRate = latest.getBurnRatePerDay() != null ? latest.getBurnRatePerDay() : BigDecimal.ZERO;
                forecastExhaustion = latest.getForecastExhaustion() != null
                    ? latest.getForecastExhaustion().toString() : "N/A";
            }

            BigDecimal purchased = budget.getPurchasedDays() != null ? budget.getPurchasedDays() : BigDecimal.ONE;
            int burnPct = purchased.compareTo(BigDecimal.ZERO) > 0
                ? burned.multiply(BigDecimal.valueOf(100)).divide(purchased, 0, RoundingMode.HALF_UP).intValue()
                : 0;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("projectId", pid);
            result.put("purchasedDays", purchased);
            result.put("burnedDays", burned);
            result.put("remainingDays", remaining);
            result.put("burnRatePerDay", burnRate);
            result.put("burnPct", burnPct);
            result.put("forecastExhaustion", forecastExhaustion);
            result.put("alertThresholdPct", budget.getAlertThresholdPct() != null ? budget.getAlertThresholdPct() : 80);
            return result;
        }).orElseGet(() -> {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "no_budget_configured");
            err.put("projectId", pid);
            return err;
        });
    }
}
