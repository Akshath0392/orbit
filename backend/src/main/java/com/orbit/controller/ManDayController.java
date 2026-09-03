package com.orbit.controller;

import com.orbit.domain.client.ManDayBudget;
import com.orbit.domain.client.Project;
import com.orbit.repository.*;
import com.orbit.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/man-days")
public class ManDayController {

    private final ManDayBudgetRepository budgets;
    private final ManDaySnapshotRepository snapshots;
    private final ProjectRepository projects;
    private final JwtService jwtService;

    public ManDayController(ManDayBudgetRepository budgets,
                             ManDaySnapshotRepository snapshots,
                             ProjectRepository projects,
                             JwtService jwtService) {
        this.budgets = budgets; this.snapshots = snapshots; this.projects = projects;
        this.jwtService = jwtService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PM','ENGINEERING','ADMIN')")
    public List<Map<String,Object>> list() {
        return projects.findByActiveTrue().stream().map(p -> {
            var budget = budgets.findByProjectId(p.getId());
            var snaps  = snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(p.getId());
            double purchased = budget.map(b -> b.getPurchasedDays().doubleValue()).orElse(100.0);
            double burned    = snaps.isEmpty() ? purchased * 0.5
                : snaps.get(0).getBurnedDays().doubleValue();
            double rate      = snaps.size() > 1 && snaps.get(0).getBurnRatePerDay() != null
                ? snaps.get(0).getBurnRatePerDay().doubleValue() : 1.0;
            double burnPct   = purchased > 0 ? burned / purchased * 100 : 0;
            String status    = burnPct >= 90 ? "critical" : burnPct >= 75 ? "warn" : "healthy";
            String exhaustion = snaps.isEmpty() || snaps.get(0).getForecastExhaustion() == null
                ? "Unknown" : snaps.get(0).getForecastExhaustion().toString();
            int threshold    = budget.map(ManDayBudget::getAlertThresholdPct).orElse(80);
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("client", p.getClient() != null ? p.getClient().getName() : "—");
            m.put("total", (int)purchased);
            m.put("burned", (int)burned);
            m.put("rate", rate);
            m.put("exh", exhaustion);
            m.put("st", status);
            m.put("burnPct", (int)burnPct);
            m.put("alertThresholdPct", threshold);
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/forecast")
    @PreAuthorize("hasAnyRole('PM','ENGINEERING','ADMIN')")
    public Map<String,Object> forecast(@RequestParam Long projectId) {
        var snap   = snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(projectId);
        var budget = budgets.findByProjectId(projectId);

        double purchased = budget.map(b -> b.getPurchasedDays() != null ? b.getPurchasedDays().doubleValue() : 100.0).orElse(100.0);
        double burned    = snap.isEmpty() || snap.get(0).getBurnedDays() == null ? purchased * 0.5
                         : snap.get(0).getBurnedDays().doubleValue();
        double rate      = snap.size() > 1 && snap.get(0).getBurnRatePerDay() != null
                         ? snap.get(0).getBurnRatePerDay().doubleValue() : 1.2;

        double remaining    = Math.max(0, purchased - burned);
        int    daysToExhaust = rate > 0 ? (int) Math.ceil(remaining / rate) : 999;
        LocalDate exhaustion = LocalDate.now().plusDays(daysToExhaust);

        List<Map<String,Object>> points = new ArrayList<>();
        for (int i = 0; i <= 30; i++) {
            double y  = Math.min(purchased, burned + rate * i);
            double ci = rate * 0.15 * Math.sqrt(i + 1);
            Map<String,Object> pt = new LinkedHashMap<>();
            pt.put("ds",          LocalDate.now().plusDays(i).toString());
            pt.put("yhat",        Math.round(y * 10.0) / 10.0);
            pt.put("yhatLower80", Math.round(Math.max(0, y - ci) * 10.0) / 10.0);
            pt.put("yhatUpper80", Math.round(Math.min(purchased, y + ci) * 10.0) / 10.0);
            pt.put("yhatLower95", Math.round(Math.max(0, y - ci * 1.8) * 10.0) / 10.0);
            pt.put("yhatUpper95", Math.round(Math.min(purchased, y + ci * 1.8) * 10.0) / 10.0);
            points.add(pt);
        }

        int alertThreshold = budget.map(b -> b.getAlertThresholdPct() != null ? b.getAlertThresholdPct() : 80).orElse(80);
        double burnPct = purchased > 0 ? burned / purchased * 100 : 0;
        String interpretation = String.format(
            "At %.1f MD/day, budget exhausts around %s (%d days). "
            + "%.0f%% burned of %d MD purchased. Alert threshold: %d%%.",
            rate, exhaustion, daysToExhaust, burnPct, (int)purchased, alertThreshold
        );

        List<String> proposedActions = new ArrayList<>();
        if (burnPct > 85)
            proposedActions.add("Immediately review scope — budget critical with " + (int)remaining + " MD remaining.");
        if (rate > 1.5)
            proposedActions.add("Burn rate elevated (" + String.format("%.1f", rate) + " MD/day). Review parallel workstreams.");
        if (daysToExhaust < 30)
            proposedActions.add("Escalate budget alert to client — exhaustion within " + daysToExhaust + " days.");
        if (proposedActions.isEmpty())
            proposedActions.add("Budget on track. Continue monitoring weekly.");

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("forecast",         points);
        r.put("purchased",        (int)purchased);
        r.put("burned",           Math.round(burned * 10.0) / 10.0);
        r.put("rate",             Math.round(rate * 100.0) / 100.0);
        r.put("exhaustionDate",   exhaustion.toString());
        r.put("daysToExhaust",    daysToExhaust);
        r.put("alertThresholdPct",alertThreshold);
        r.put("interpretation",   interpretation);
        r.put("proposedActions",  proposedActions);
        return r;
    }

    @PutMapping("/budget")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> updateBudget(@RequestParam Long projectId,
                                           @RequestBody Map<String,Object> body,
                                           Authentication auth,
                                           HttpServletRequest request) {
        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            String header = request.getHeader("Authorization");
            if (header == null || !jwtService.canEditBudget(header.substring(7))) {
                return ResponseEntity.status(403).body(Map.of("error", "budget_edit_not_permitted"));
            }
        }
        return projects.findById(projectId).map(p -> {
            ManDayBudget b = budgets.findByProjectId(projectId)
                .orElse(ManDayBudget.builder().project(p).build());
            if (body.get("purchasedDays") != null)
                b.setPurchasedDays(new BigDecimal(body.get("purchasedDays").toString()));
            if (body.get("alertThresholdPct") != null)
                b.setAlertThresholdPct(Integer.parseInt(body.get("alertThresholdPct").toString()));
            if (body.get("periodStart") != null)
                b.setPeriodStart(LocalDate.parse((String)body.get("periodStart")));
            if (body.get("periodEnd") != null)
                b.setPeriodEnd(LocalDate.parse((String)body.get("periodEnd")));
            budgets.save(b);
            Map<String,Object> r = new LinkedHashMap<>();
            r.put("projectId", projectId); r.put("updated", true);
            return ResponseEntity.ok(r);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/portfolio-summary")
    @PreAuthorize("hasAnyRole('PM','ENGINEERING','REVENUE','ADMIN')")
    public Map<String, Object> portfolioSummary(@RequestParam Long portfolioId) {
        List<Project> portfolioProjects = projects.findByPortfolioIdAndActiveTrue(portfolioId);

        java.math.BigDecimal totalPurchased = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalBurned    = java.math.BigDecimal.ZERO;

        for (Project p : portfolioProjects) {
            var budget = budgets.findByProjectId(p.getId());
            if (budget.isPresent()) {
                totalPurchased = totalPurchased.add(
                    budget.get().getPurchasedDays() != null ? budget.get().getPurchasedDays() : java.math.BigDecimal.ZERO);
            }
            var snaps = snapshots.findTop14ByProjectIdOrderBySnapshotDateDesc(p.getId());
            if (!snaps.isEmpty() && snaps.get(0).getBurnedDays() != null) {
                totalBurned = totalBurned.add(snaps.get(0).getBurnedDays());
            }
        }

        java.math.BigDecimal remaining = totalPurchased.subtract(totalBurned);
        int burnPct = totalPurchased.compareTo(java.math.BigDecimal.ZERO) > 0
            ? totalBurned.multiply(java.math.BigDecimal.valueOf(100))
                .divide(totalPurchased, 0, RoundingMode.HALF_UP).intValue()
            : 0;

        return Map.of(
            "portfolioId",    portfolioId,
            "soldMandays",    totalPurchased,
            "consumedMandays",totalBurned,
            "remainingMandays",remaining,
            "burnPct",        burnPct,
            "projectCount",   portfolioProjects.size()
        );
    }
}
