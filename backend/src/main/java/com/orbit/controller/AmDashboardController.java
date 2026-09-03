package com.orbit.controller;

import com.orbit.domain.client.Client;
import com.orbit.domain.client.LifecycleMapping;
import com.orbit.domain.client.Portfolio;
import com.orbit.domain.client.Project;
import com.orbit.domain.config.AmSettings;
import com.orbit.domain.config.StageSlaTarget;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.AmSettingsRepository;
import com.orbit.repository.ClientRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.LifecycleMappingRepository;
import com.orbit.repository.PortfolioRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.repository.StageSlaTargetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Account Management dashboard aggregations (docs/plan/orbitter-am-dashboard-plan.md).
 * All endpoints are read-only rollups over already-synced Jira data, scoped by
 * optional portfolioId (mock "POD"). Stage %-within-SLA compares each open CR's
 * age against the configurable per-stage target (stage_sla_targets); stages
 * without a target (e.g. Hold) are counted but not SLA-scored — mock parity.
 */
@RestController
@RequestMapping("/api/v1/am")
@PreAuthorize("isAuthenticated()")
public class AmDashboardController {

    private static final String HOLD_STAGE = "Hold";
    private static final String UNSTAGED = "Unstaged";
    private static final String UNASSIGNED = "Unassigned";

    private final JiraIssueRepository issues;
    private final StageSlaTargetRepository targets;
    private final LifecycleMappingRepository lifecycle;
    private final PortfolioRepository portfolios;
    private final ClientRepository clients;
    private final ProjectRepository projects;
    private final AmSettingsRepository settings;
    private final com.orbit.repository.JiraConfigRepository jiraConfigs;
    private final com.orbit.service.sync.VelocityService velocity;
    private final com.orbit.repository.ProjectReleaseRepository releases;
    private final com.orbit.service.am.SlaBucketService sla;

    public AmDashboardController(JiraIssueRepository issues,
                                 StageSlaTargetRepository targets,
                                 LifecycleMappingRepository lifecycle,
                                 PortfolioRepository portfolios,
                                 ClientRepository clients,
                                 ProjectRepository projects,
                                 AmSettingsRepository settings,
                                 com.orbit.repository.JiraConfigRepository jiraConfigs,
                                 com.orbit.service.sync.VelocityService velocity,
                                 com.orbit.repository.ProjectReleaseRepository releases,
                                 com.orbit.service.am.SlaBucketService sla) {
        this.issues = issues;
        this.targets = targets;
        this.lifecycle = lifecycle;
        this.portfolios = portfolios;
        this.clients = clients;
        this.projects = projects;
        this.settings = settings;
        this.jiraConfigs = jiraConfigs;
        this.velocity = velocity;
        this.releases = releases;
        this.sla = sla;
    }

    /** "On Hold"/"Blocked" style stages collapse into the mock's single Hold bucket. */
    private static String normaliseStage(String stage) {
        if (stage == null || stage.isBlank()) return UNSTAGED;
        String s = stage.trim();
        return s.equalsIgnoreCase("on hold") || s.equalsIgnoreCase("hold") ? HOLD_STAGE : s;
    }

    // row indices from JiraIssueRepository.findOpenAmCrRows
    private record CrRow(String client, String stage, String owner, long ageDays, String ops,
                         String smOwner, String pjmOwner) {}

    private List<CrRow> openRows(Long portfolioId, String type) {
        String want = normaliseType(type);
        LocalDateTime now = LocalDateTime.now();
        return issues.findOpenAmCrRows(portfolioId).stream()
            .filter(r -> want == null || (r[3] != null && ((String) r[3]).toLowerCase().contains(want)))
            .map(r -> new CrRow(
                r[0] == null ? "Unknown" : ((String) r[0]).trim(),
                normaliseStage((String) r[1]),
                r[2] == null || ((String) r[2]).isBlank() ? UNASSIGNED : (String) r[2],
                r[4] == null ? 0 : ChronoUnit.DAYS.between((LocalDateTime) r[4], now),
                r[3] == null ? "" : ((String) r[3]).toLowerCase(),
                r.length > 5 ? (String) r[5] : null,
                r.length > 6 ? (String) r[6] : null))
            .toList();
    }

    private static String normaliseType(String type) {
        if (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type)) return null;
        return type.toLowerCase(); // matches ops_model: launch | bau | launch+bau
    }

    /** Ordered stage list: lifecycle display_order first, unknown stages after, Hold/Unstaged last. */
    private List<String> orderStages(Collection<String> present) {
        Map<String, Integer> order = new HashMap<>();
        for (LifecycleMapping m : lifecycle.findAll()) {
            if (m.getGaugeStage() != null) {
                order.merge(m.getGaugeStage(),
                    m.getDisplayOrder() == null ? 500 : m.getDisplayOrder(), Math::min);
            }
        }
        return present.stream()
            .sorted(Comparator.comparingInt(s ->
                HOLD_STAGE.equals(s) ? 900 : UNSTAGED.equals(s) ? 950 : order.getOrDefault(s, 800)))
            .toList();
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(required = false) Long portfolioId,
                                       @RequestParam(required = false) String type) {
        List<CrRow> rows = openRows(portfolioId, type);
        long hold = rows.stream().filter(r -> HOLD_STAGE.equals(r.stage())).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("openCrs", rows.size() - hold);
        out.put("clientHold", hold);
        out.put("clients", rows.stream().map(CrRow::client).distinct().count());
        return out;
    }

    @GetMapping("/stage-matrix")
    public Map<String, Object> stageMatrix(@RequestParam(required = false) Long portfolioId,
                                           @RequestParam(required = false) String type) {
        List<CrRow> rows = openRows(portfolioId, type);
        Map<String, Integer> targetByStage = targets.findAll().stream()
            .collect(Collectors.toMap(StageSlaTarget::getStage, StageSlaTarget::getTargetDays));

        // clients ordered by open volume, mock-style
        List<String> clients = rows.stream()
            .collect(Collectors.groupingBy(CrRow::client, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(Map.Entry::getKey).toList();
        List<String> stages = orderStages(rows.stream().map(CrRow::stage).collect(Collectors.toSet()));

        Map<String, Map<String, List<CrRow>>> byStageClient = rows.stream()
            .collect(Collectors.groupingBy(CrRow::stage, Collectors.groupingBy(CrRow::client)));

        List<Map<String, Object>> stageRows = new ArrayList<>();
        for (String stage : stages) {
            Map<String, List<CrRow>> byClient = byStageClient.getOrDefault(stage, Map.of());
            Integer target = targetByStage.get(stage);
            List<CrRow> all = byClient.values().stream().flatMap(List::stream).toList();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stage", stage);
            row.put("targetDays", target);
            row.put("total", all.size());
            row.put("avgAgingDays", Math.round(all.stream().mapToLong(CrRow::ageDays).average().orElse(0)));
            row.put("medianAgingDays", medianAge(all));
            // Canonical 3-bucket SLA — all rows here share this stage's target.
            if (target == null || all.isEmpty()) {
                row.put("met", null);
                row.put("near", null);
                row.put("breached", null);
                row.put("withinSlaPct", null);
            } else {
                var b = sla.compute(all, CrRow::ageDays, r -> target);
                row.put("met", b.met());
                row.put("near", b.near());
                row.put("breached", b.breached());
                row.put("withinSlaPct", b.adherencePct());
            }
            Map<String, Object> cells = new LinkedHashMap<>();
            for (String client : clients) {
                List<CrRow> cell = byClient.getOrDefault(client, List.of());
                if (cell.isEmpty()) continue;
                cells.put(client, Map.of(
                    "count", cell.size(),
                    "avgAgingDays", Math.round(cell.stream().mapToLong(CrRow::ageDays).average().orElse(0))));
            }
            row.put("cells", cells);
            stageRows.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("clients", clients);
        out.put("clientTotals", clients.stream().collect(Collectors.toMap(c -> c,
            c -> rows.stream().filter(r -> r.client().equals(c)).count(),
            (a, b) -> a, LinkedHashMap::new)));
        out.put("stages", stageRows);
        out.put("total", rows.size());
        return out;
    }

    @GetMapping("/owner-matrix")
    public Map<String, Object> ownerMatrix(@RequestParam(required = false) Long portfolioId,
                                           @RequestParam(required = false) String type) {
        List<CrRow> rows = openRows(portfolioId, type);
        List<String> stages = orderStages(rows.stream().map(CrRow::stage).collect(Collectors.toSet()));
        List<String> owners = rows.stream()
            .collect(Collectors.groupingBy(CrRow::owner, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(Map.Entry::getKey).toList();

        List<Map<String, Object>> ownerRows = new ArrayList<>();
        for (String owner : owners) {
            List<CrRow> mine = rows.stream().filter(r -> r.owner().equals(owner)).toList();
            Map<String, Long> byStage = mine.stream()
                .collect(Collectors.groupingBy(CrRow::stage, Collectors.counting()));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("owner", owner);
            row.put("total", mine.size());
            row.put("byStage", byStage);
            ownerRows.add(row);
        }
        return Map.of("stages", stages, "owners", ownerRows, "total", rows.size());
    }

    @GetMapping("/prod-trend")
    public Map<String, Object> prodTrend(@RequestParam(required = false) Long portfolioId,
                                         @RequestParam(defaultValue = "12") int months,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        YearMonth start;
        int m;
        if (from != null && !from.isBlank()) {
            // explicit window (quarter dropdown / custom range), yyyy-MM inclusive
            start = YearMonth.parse(from);
            YearMonth end = to == null || to.isBlank() ? YearMonth.from(LocalDate.now()) : YearMonth.parse(to);
            m = (int) Math.min(Math.max(ChronoUnit.MONTHS.between(start, end) + 1, 1), 24);
        } else {
            m = Math.min(Math.max(months, 1), 24);
            start = YearMonth.from(LocalDate.now()).minusMonths(m - 1L);
        }
        LocalDateTime since = start.atDay(1).atStartOfDay();

        int[] created = new int[m];
        int[] closed = new int[m];
        long openNow = 0;
        Map<String, Long> openBySeverity = new TreeMap<>();
        for (Object[] r : issues.findAmProdBugRows(portfolioId, since)) {
            LocalDateTime c = (LocalDateTime) r[0];
            LocalDateTime res = (LocalDateTime) r[1];
            if (c != null) {
                int i = (int) ChronoUnit.MONTHS.between(start, YearMonth.from(c));
                if (i >= 0 && i < m) created[i]++;
            }
            if (res != null) {
                int i = (int) ChronoUnit.MONTHS.between(start, YearMonth.from(res));
                if (i >= 0 && i < m) closed[i]++;
            } else {
                openNow++;
                String sev = r[2] == null ? "Unclassified" : (String) r[2];
                openBySeverity.merge(sev, 1L, Long::sum);
            }
        }
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < m; i++) labels.add(start.plusMonths(i).toString()); // yyyy-MM

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("months", labels);
        out.put("created", created);
        out.put("closed", closed);
        out.put("openNow", openNow);
        out.put("openBySeverity", openBySeverity);
        return out;
    }

    @GetMapping("/clients")
    public List<Map<String, Object>> clients(@RequestParam(required = false) Long portfolioId) {
        List<CrRow> rows = openRows(portfolioId, null);
        Map<String, List<CrRow>> byClient = rows.stream().collect(Collectors.groupingBy(CrRow::client));
        Map<String, Map<String, Long>> prodByClient = new HashMap<>();
        for (Object[] r : issues.countOpenProdBugsByClientAndSeverity(portfolioId)) {
            // trim + merge: live data has clients duplicated by trailing whitespace
            prodByClient.computeIfAbsent(r[0] == null ? "Unknown" : ((String) r[0]).trim(), k -> new TreeMap<>())
                .merge(r[1] == null ? "Unclassified" : (String) r[1], (Long) r[2], Long::sum);
        }
        Set<String> names = new TreeSet<>(byClient.keySet());
        names.addAll(prodByClient.keySet());
        // active rows only — a retired duplicate sharing the name must not
        // hijack the tile's clientId (its master page renders empty)
        Map<String, Client> clientByName = new HashMap<>();
        for (Client c : clients.findByActiveTrue()) {
            if (c.getName() != null) clientByName.putIfAbsent(c.getName().trim(), c);
        }
        // client → its account page (first active project); mock §4.3 tile click
        Map<Long, Long> projectByClient = new HashMap<>();
        for (Project p : projects.findByActiveTrue()) {
            if (p.getClient() != null) projectByClient.putIfAbsent(p.getClient().getId(), p.getId());
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (String name : names) {
            List<CrRow> crs = byClient.getOrDefault(name, List.of());
            Client client = clientByName.get(name.trim());
            Long clientId = client == null ? null : client.getId();
            Map<String, Object> tile = new LinkedHashMap<>();
            tile.put("client", name);
            tile.put("clientId", clientId);
            tile.put("projectId", clientId == null ? null : projectByClient.get(clientId));
            // D3 — health chip = weighted DH pillar score, same math as the pillar tabs
            tile.put("healthScore", clientId == null ? null : dhHealthScore(clientId));
            tile.put("healthGreenThreshold", client == null ? 80 : client.getHealthGreenThreshold());
            tile.put("healthAmberThreshold", client == null ? 60 : client.getHealthAmberThreshold());
            tile.put("openCrs", crs.size());
            tile.put("openBauCrs", crs.stream().filter(r -> r.ops().contains("bau")).count());
            tile.put("openLaunchCrs", crs.stream().filter(r -> r.ops().contains("launch")).count());
            tile.put("avgAgingDays", Math.round(crs.stream().mapToLong(CrRow::ageDays).average().orElse(0)));
            tile.put("openProdBySeverity", prodByClient.getOrDefault(name, Map.of()));
            tile.put("velocitySoS", clientId == null ? null
                : velocity.velocityPayload(null, clientId, 6).get("velocitySoS"));
            out.add(tile);
        }
        out.sort((a, b) -> Integer.compare((int) (Integer) b.get("openCrs"), (int) (Integer) a.get("openCrs")));
        return out;
    }

    /** Weighted DH health (D3): am_settings weights over the pillars that have data. */
    private Long dhHealthScore(Long clientId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> pillars = (Map<String, Object>) dhPayload(clientId, 6, null).get("pillars");
        AmSettings s = settings.findById(1L).orElseGet(AmSettings::new);
        Map<String, Integer> weights = Map.of(
            "speed", s.getDhSpeedWeight(), "quality", s.getDhQualityWeight(), "pred", s.getDhPredWeight());
        double sum = 0;
        int wsum = 0;
        for (Map.Entry<String, Integer> w : weights.entrySet()) {
            Integer v = (Integer) pillars.get(w.getKey());
            if (v != null) { sum += v * w.getValue(); wsum += w.getValue(); }
        }
        return wsum == 0 ? null : Math.round(sum / wsum);
    }

    @GetMapping("/crs")
    public Page<Map<String, Object>> crDrill(@RequestParam(required = false) Long portfolioId,
                                             @RequestParam(required = false) Long clientId,
                                             @RequestParam(required = false) String clientName,
                                             @RequestParam(required = false) String stage,
                                             @RequestParam(required = false) String owner,
                                             @RequestParam(required = false) String smOwner,
                                             @RequestParam(required = false) String pjmOwner,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        String want = normaliseType(type);
        String opsLike = want == null ? null : "%" + want + "%";
        // Trim in Java and null-out blanks: the query matches against TRIM(client.name)
        // but must NOT wrap this bind in a SQL TRIM() — a null bind inside TRIM() is sent
        // to Postgres as bytea and fails as btrim(bytea) (SQLState 42883).
        String clientNameFilter = (clientName == null || clientName.isBlank()) ? null : clientName.trim();
        LocalDateTime now = LocalDateTime.now();
        return issues.findAmCrDrill(portfolioId, clientId, clientNameFilter, stage, owner, smOwner, pjmOwner, opsLike,
                PageRequest.of(page, Math.min(size, 100)))
            .map(j -> toDrillRow(j, now));
    }

    // ── V3 reforms (docs/plan/orbitter-am-v3-reforms-plan.md) ───────────────

    /**
     * POD benchmarking scores — mock parity (D2): min-max normalized
     * 60% CSAT (avg of admin-entered Launch/BAU, F1) + 40% SLA adherence.
     * PODs without any client CSAT fall back to absolute SLA adherence %
     * (never a fake 0); all-CSAT-missing degrades to the old interim scoring.
     */
    @GetMapping("/pod-score")
    public List<Map<String, Object>> podScore() {
        Map<String, Integer> targetByStage = targets.findAll().stream()
            .collect(Collectors.toMap(StageSlaTarget::getStage, StageSlaTarget::getTargetDays));
        LocalDateTime now = LocalDateTime.now();

        Map<Long, List<Object[]>> crsByPod = issues.findOpenCrRowsAllPortfolios().stream()
            .collect(Collectors.groupingBy(r -> (Long) r[0]));
        Map<Long, Map<String, Long>> prodByPod = new HashMap<>();
        for (Object[] r : issues.countOpenProdBugsByPortfolioAndSeverity()) {
            prodByPod.computeIfAbsent((Long) r[0], k -> new TreeMap<>())
                .put(r[1] == null ? "Unclassified" : (String) r[1], (Long) r[2]);
        }

        record PodCalc(Map<String, Object> payload, Double csat, Double sla) {}
        List<PodCalc> calcs = new ArrayList<>();
        for (Portfolio pf : portfolios.findByActiveTrue()) {
            List<Object[]> rows = crsByPod.getOrDefault(pf.getId(), List.of());
            long met = 0, near = 0, breached = 0, bau = 0, launch = 0;
            for (Object[] r : rows) {
                String stage = (String) r[2];
                String ops = r[4] == null ? "" : ((String) r[4]).toLowerCase();
                if (ops.contains("bau")) bau++;
                if (ops.contains("launch")) launch++;
                Integer target = stage == null ? null : targetByStage.get(stage);
                long age = r[3] == null ? 0 : ChronoUnit.DAYS.between((LocalDateTime) r[3], now);
                // Canonical 3-bucket SLA — same classifier as the stage matrix.
                var bucket = sla.classify(age, target);
                if (bucket == null) continue; // untracked stage
                switch (bucket) {
                    case MET -> met++;
                    case NEAR -> near++;
                    case BREACHED -> breached++;
                }
            }
            long tracked = met + near + breached;

            // POD CSAT = simple avg of its clients' non-null values (F1)
            Double csatL = avgDecimal(pf.getClients(), Client::getCsatLaunch);
            Double csatB = avgDecimal(pf.getClients(), Client::getCsatBau);
            Double csat = avgNullable(csatL, csatB);
            // Adherence = met / tracked (met excludes the near band) — canonical definition.
            Double slaAdh = tracked == 0 ? null : (double) met / tracked;

            Map<String, Long> prod = prodByPod.getOrDefault(pf.getId(), Map.of());
            Map<String, Object> pod = new LinkedHashMap<>();
            pod.put("portfolioId", pf.getId());
            pod.put("name", pf.getName());
            pod.put("csatLaunch", csatL == null ? null : Math.round(csatL * 10) / 10.0);
            pod.put("csatBau", csatB == null ? null : Math.round(csatB * 10) / 10.0);
            pod.put("slaTracked", tracked);
            pod.put("slaMet", met);
            pod.put("slaNear", near);
            pod.put("slaBreached", breached);
            pod.put("openBauCrs", bau);
            pod.put("openLaunchCrs", launch);
            pod.put("prodOpen", prod.values().stream().mapToLong(Long::longValue).sum());
            pod.put("prodBySeverity", prod);
            pod.put("velocitySoS", velocity.velocityPayload(pf.getId(), null, 6).get("velocitySoS"));
            calcs.add(new PodCalc(pod, csat, slaAdh));
        }

        // Min-max over the PODs that have each input (mock podScore); a single
        // POD (or all-equal values) normalizes to 1, not 0.
        double[] csatRange = range(calcs.stream().map(PodCalc::csat).filter(Objects::nonNull).toList());
        double[] slaRange = range(calcs.stream().map(PodCalc::sla).filter(Objects::nonNull).toList());
        for (PodCalc c : calcs) {
            Long score;
            String basis;
            if (c.csat() != null && c.sla() != null) {
                score = Math.round(100 * (0.6 * norm(c.csat(), csatRange) + 0.4 * norm(c.sla(), slaRange)));
                basis = "60% CSAT + 40% SLA adherence, min-max normalized across PODs";
            } else if (c.csat() != null) {
                score = Math.round(100 * norm(c.csat(), csatRange));
                basis = "CSAT only (no SLA-tracked CRs), min-max normalized";
            } else if (c.sla() != null) {
                score = Math.round(100 * c.sla());
                basis = "SLA adherence only (no client CSAT entered — absolute, not normalized)";
            } else {
                score = null;
                basis = "no CSAT or SLA-tracked CRs yet";
            }
            c.payload().put("score", score);
            c.payload().put("scoreBasis", basis);
        }

        List<Map<String, Object>> pods = calcs.stream().map(PodCalc::payload)
            .collect(Collectors.toCollection(ArrayList::new));
        pods.sort(Comparator.comparing(p -> (Long) ((Map<String, Object>) p).get("score"),
            Comparator.nullsLast(Comparator.reverseOrder())));
        for (int i = 0; i < pods.size(); i++) pods.get(i).put("rank", i + 1);
        return pods;
    }

    private static Double avgDecimal(Collection<Client> clients,
                                     java.util.function.Function<Client, java.math.BigDecimal> get) {
        List<Double> vals = clients.stream().map(get).filter(Objects::nonNull)
            .map(java.math.BigDecimal::doubleValue).toList();
        return vals.isEmpty() ? null : vals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    /** Boxed-safe average — plain ternaries auto-unbox and NPE on a null half. */
    private static Double avgNullable(Double a, Double b) {
        if (a == null) return b;
        if (b == null) return a;
        return (a + b) / 2;
    }

    private static double[] range(List<Double> vals) {
        if (vals.isEmpty()) return new double[]{0, 0};
        double min = vals.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = vals.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        return new double[]{min, max};
    }

    private static double norm(double v, double[] range) {
        return range[1] == range[0] ? 1 : (v - range[0]) / (range[1] - range[0]);
    }

    /**
     * Week-on-week production drill: W1(1–7)/W2(8–14)/W3(15–21)/W4(22–end)
     * created/resolved counts per month, plus a running open line that walks
     * back from today's open count so the last point equals "open now".
     */
    @GetMapping("/prod-weekly")
    public Map<String, Object> prodWeekly(@RequestParam(required = false) Long portfolioId,
                                          @RequestParam String from,
                                          @RequestParam(required = false) String to) {
        YearMonth start = YearMonth.parse(from);
        YearMonth end = to == null || to.isBlank() ? YearMonth.from(LocalDate.now()) : YearMonth.parse(to);
        int m = (int) Math.min(Math.max(ChronoUnit.MONTHS.between(start, end) + 1, 1), 12);

        int[][] created = new int[m][4];
        int[][] resolved = new int[m][4];
        long openNow = 0;
        for (Object[] r : issues.findAmProdBugRows(portfolioId, start.atDay(1).atStartOfDay())) {
            LocalDateTime c = (LocalDateTime) r[0];
            LocalDateTime res = (LocalDateTime) r[1];
            if (c != null) bump(created, start, m, c);
            if (res == null) openNow++;
            else bump(resolved, start, m, res);
        }
        long totC = Arrays.stream(created).flatMapToInt(Arrays::stream).sum();
        long totR = Arrays.stream(resolved).flatMapToInt(Arrays::stream).sum();
        long open = openNow - totC + totR;

        List<Map<String, Object>> monthsOut = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            long[] openWeeks = new long[4];
            for (int w = 0; w < 4; w++) {
                open += created[i][w] - resolved[i][w];
                openWeeks[w] = open;
            }
            Map<String, Object> mo = new LinkedHashMap<>();
            mo.put("month", start.plusMonths(i).toString());
            mo.put("created", created[i]);
            mo.put("resolved", resolved[i]);
            mo.put("open", openWeeks);
            monthsOut.add(mo);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("months", monthsOut);
        out.put("openNow", openNow);
        return out;
    }

    private static void bump(int[][] buckets, YearMonth start, int m, LocalDateTime when) {
        int i = (int) ChronoUnit.MONTHS.between(start, YearMonth.from(when));
        if (i < 0 || i >= m) return;
        buckets[i][Math.min((when.getDayOfMonth() - 1) / 7, 3)]++;
    }

    /**
     * Owner share of open CRs (donut source). dim = assignee (default) | sm |
     * pjm (mock W9/W10 — needs the matching jira_config field mapping; when
     * unmapped the payload says so and the UI shows a setup hint instead of
     * silently wrong assignee data). Top-N + Others grouping is client-side.
     */
    @GetMapping("/owner-share")
    public Map<String, Object> ownerShare(@RequestParam(required = false) Long portfolioId,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(defaultValue = "assignee") String dim) {
        if (("sm".equals(dim) || "pjm".equals(dim)) && !ownerDimConfigured(dim)) {
            return Map.of("owners", List.of(), "total", 0, "dim", dim, "configured", false);
        }
        List<CrRow> rows = openRows(portfolioId, type);
        java.util.function.Function<CrRow, String> ownerOf = switch (dim) {
            case "sm" -> r -> r.smOwner() == null || r.smOwner().isBlank() ? UNASSIGNED : r.smOwner();
            case "pjm" -> r -> r.pjmOwner() == null || r.pjmOwner().isBlank() ? UNASSIGNED : r.pjmOwner();
            default -> CrRow::owner;
        };
        List<Map<String, Object>> owners = rows.stream()
            .collect(Collectors.groupingBy(ownerOf, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(e -> {
                Map<String, Object> o = new LinkedHashMap<String, Object>();
                o.put("owner", e.getKey());
                o.put("count", e.getValue());
                return o;
            })
            .toList();
        return Map.of("owners", owners, "total", rows.size(), "dim", dim, "configured", true);
    }

    private boolean ownerDimConfigured(String dim) {
        return jiraConfigs.findFirstByOrderByIdAsc()
            .map(c -> "sm".equals(dim) ? c.getSmField() : c.getPjmField())
            .filter(f -> f != null && !f.isBlank())
            .isPresent();
    }

    // ── Client master page (clients/:id) ────────────────────────────────────

    private record ClientCr(String stage, long ageDays, String ops) {}

    private List<ClientCr> clientOpenCrs(Long clientId, String type) {
        String want = normaliseType(type);
        LocalDateTime now = LocalDateTime.now();
        return issues.findOpenCrRowsForClient(clientId).stream()
            .filter(r -> want == null || (r[2] != null && ((String) r[2]).toLowerCase().contains(want)))
            .map(r -> new ClientCr(
                normaliseStage((String) r[0]),
                r[1] == null ? 0 : ChronoUnit.DAYS.between((LocalDateTime) r[1], now),
                r[2] == null ? "" : ((String) r[2]).toLowerCase()))
            .toList();
    }

    /**
     * Overview tab rollup. Breached·Near·Met over open CRs in SLA-tracked
     * stages: met = comfortable time left, near = &lt;25% of the window
     * remaining (mock's early-warning band), breached = past target.
     * CSAT and utilization have no source yet — returned null, UI shows "—".
     */
    @GetMapping("/client/{id}/overview")
    public Map<String, Object> clientOverview(@PathVariable Long id) {
        Client client = clients.findById(id).orElseThrow();
        List<ClientCr> rows = clientOpenCrs(id, null);
        Map<String, Integer> targetByStage = targets.findAll().stream()
            .collect(Collectors.toMap(StageSlaTarget::getStage, StageSlaTarget::getTargetDays));

        // Canonical 3-bucket SLA — one classifier shared with the
        // stage matrix, POD score and DH-metrics so the same client can never
        // show different SLA numbers on different surfaces.
        var buckets = sla.compute(rows, ClientCr::ageDays, r -> targetByStage.get(r.stage()));
        long met = buckets.met(), near = buckets.near(), breached = buckets.breached();
        long tracked = buckets.tracked();

        List<Map<String, Object>> stageRows = new ArrayList<>();
        Map<String, List<ClientCr>> byStage = rows.stream().collect(Collectors.groupingBy(ClientCr::stage));
        for (String stage : orderStages(byStage.keySet())) {
            List<ClientCr> in = byStage.get(stage);
            Integer target = targetByStage.get(stage);
            var stageBuckets = sla.compute(in, ClientCr::ageDays, r -> target);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stage", stage);
            row.put("targetDays", target);
            row.put("total", in.size());
            row.put("avgAgingDays", Math.round(in.stream().mapToLong(ClientCr::ageDays).average().orElse(0)));
            row.put("met", target == null ? null : stageBuckets.met());
            row.put("near", target == null ? null : stageBuckets.near());
            row.put("breached", target == null ? null : stageBuckets.breached());
            row.put("withinSlaPct", target == null || in.isEmpty() ? null : stageBuckets.adherencePct());
            stageRows.add(row);
        }

        Map<String, Long> prodBySeverity = new TreeMap<>();
        for (Object[] r : issues.findProdBugRowsForClient(id, LocalDateTime.now().minusYears(1))) {
            if (r[1] != null) continue; // resolved
            prodBySeverity.merge(r[2] == null ? "Unclassified" : (String) r[2], 1L, Long::sum);
        }

        String pod = portfolios.findByClientsIdAndActiveTrue(id).stream()
            .map(Portfolio::getName).findFirst().orElse(null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("clientId", client.getId());
        out.put("client", client.getName());
        out.put("code", client.getCode());
        out.put("pod", pod);
        out.put("healthGreenThreshold", client.getHealthGreenThreshold());
        out.put("healthAmberThreshold", client.getHealthAmberThreshold());
        // F1 — admin-entered CSAT; overview headline = avg of the two halves
        Double csatL = client.getCsatLaunch() == null ? null : client.getCsatLaunch().doubleValue();
        Double csatB = client.getCsatBau() == null ? null : client.getCsatBau().doubleValue();
        Double csat = avgNullable(csatL, csatB);
        out.put("csat", csat == null ? null : Math.round(csat * 10) / 10.0);
        out.put("csatLaunch", csatL);
        out.put("csatBau", csatB);
        out.put("engagementScore", client.getEngagementScore());
        out.put("utilization", null);
        long hold = rows.stream().filter(r -> HOLD_STAGE.equals(r.stage())).count();
        out.put("openCrs", rows.size() - hold);
        out.put("clientHold", hold);
        out.put("openBauCrs", rows.stream().filter(r -> r.ops().contains("bau")).count());
        out.put("openLaunchCrs", rows.stream().filter(r -> r.ops().contains("launch")).count());
        out.put("prodOpen", prodBySeverity.values().stream().mapToLong(Long::longValue).sum());
        out.put("prodBySeverity", prodBySeverity);
        out.put("slaAdherencePct", tracked == 0 ? null : Math.round(100.0 * met / tracked));
        out.put("slaMet", met);
        out.put("slaNear", near);
        out.put("slaBreached", breached);
        out.put("stages", stageRows);
        // Timeline & Health (mock §5 #5) — releases across the client's projects;
        // AM-only clients with no linked project get an empty list (empty state)
        List<Long> projectIds = projects.findByClientIdAndActiveTrue(id).stream()
            .map(com.orbit.domain.client.Project::getId).toList();
        List<Map<String, Object>> releaseCal = projectIds.isEmpty() ? List.of()
            : releases.findByProjectIdInAndReleaseDateBetweenOrderByReleaseDateAsc(
                projectIds, LocalDate.now().minusDays(14), LocalDate.now().plusMonths(2))
              .stream().map(r -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("id", r.getId());
                  m.put("date", r.getReleaseDate());
                  m.put("type", r.getReleaseType());
                  m.put("label", r.getLabel());
                  m.put("rag", r.getRag());
                  return m;
              }).toList();
        out.put("releaseCalendar", releaseCal);
        return out;
    }

    /**
     * Delivery-health pillar metrics — only the ones computable from synced
     * data today (lead time, throughput, stage-SLA compliance, incidents,
     * backlog aging). Pillar scores are RAG-banded (92/62/32, handoff v1)
     * over the real metrics only; predictability has none yet → null.
     */
    @GetMapping("/client/{id}/dh-metrics")
    public Map<String, Object> clientDhMetrics(@PathVariable Long id,
                                               @RequestParam(defaultValue = "6") int months,
                                               @RequestParam(required = false) String type) {
        int m = Math.min(Math.max(months, 1), 12);
        Map<String, Object> out = dhPayload(id, m, normaliseType(type));

        // vs-POD-average per pillar (mock pillar header) — same computation over
        // every client of this client's POD, this client included.
        portfolios.findByClientsIdAndActiveTrue(id).stream().findFirst().ifPresent(pod -> {
            List<Integer> speeds = new ArrayList<>(), quals = new ArrayList<>(), preds = new ArrayList<>();
            for (Client sibling : pod.getClients()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> pillars = (Map<String, Object>) dhPayload(sibling.getId(), m, null).get("pillars");
                if (pillars.get("speed") != null) speeds.add((Integer) pillars.get("speed"));
                if (pillars.get("quality") != null) quals.add((Integer) pillars.get("quality"));
                if (pillars.get("pred") != null) preds.add((Integer) pillars.get("pred"));
            }
            Map<String, Object> podAvg = new LinkedHashMap<>();
            podAvg.put("speed", avgScore(speeds.toArray(new Integer[0])));
            podAvg.put("quality", avgScore(quals.toArray(new Integer[0])));
            podAvg.put("pred", avgScore(preds.toArray(new Integer[0])));
            podAvg.put("clients", pod.getClients().size());
            out.put("vsPodAvg", podAvg);
        });
        return out;
    }

    private Map<String, Object> dhPayload(Long id, int m, String want) {
        YearMonth start = YearMonth.from(LocalDate.now()).minusMonths(m - 1L);
        LocalDateTime since = start.atDay(1).atStartOfDay();

        long[] leadSum = new long[m];
        int[] leadN = new int[m];
        long[] cycleSum = new long[m];
        int[] cycleN = new int[m];
        int[] throughput = new int[m];
        int[] reopenedN = new int[m];
        for (Object[] r : issues.findResolvedCrRowsForClient(id, since)) {
            if (want != null && (r[2] == null || !((String) r[2]).toLowerCase().contains(want))) continue;
            LocalDateTime c = (LocalDateTime) r[0];
            LocalDateTime res = (LocalDateTime) r[1];
            int i = (int) ChronoUnit.MONTHS.between(start, YearMonth.from(res));
            if (i < 0 || i >= m) continue;
            throughput[i]++;
            if (c != null) { leadSum[i] += ChronoUnit.DAYS.between(c, res); leadN[i]++; }
            if (r[3] != null && ((Integer) r[3]) > 0) reopenedN[i]++;
            // cycle time = resolution − first in-progress (changelog-derived, F3)
            if (r.length > 4 && r[4] != null) {
                cycleSum[i] += Math.max(0, ChronoUnit.DAYS.between((LocalDateTime) r[4], res));
                cycleN[i]++;
            }
        }
        long[] lead = new long[m];
        long[] cycle = new long[m];
        long[] reopened = new long[m]; // % of the month's completed work that bounced back
        boolean anyCycle = false;
        for (int i = 0; i < m; i++) {
            lead[i] = leadN[i] == 0 ? 0 : Math.round((double) leadSum[i] / leadN[i]);
            cycle[i] = cycleN[i] == 0 ? 0 : Math.round((double) cycleSum[i] / cycleN[i]);
            anyCycle |= cycleN[i] > 0;
            reopened[i] = throughput[i] == 0 ? 0 : Math.round(100.0 * reopenedN[i] / throughput[i]);
        }

        int[] incidents = new int[m];
        for (Object[] r : issues.findProdBugRowsForClient(id, since)) {
            LocalDateTime c = (LocalDateTime) r[0];
            if (c == null) continue;
            int i = (int) ChronoUnit.MONTHS.between(start, YearMonth.from(c));
            if (i >= 0 && i < m) incidents[i]++;
        }

        List<ClientCr> open = clientOpenCrs(id, want);
        Map<String, Integer> targetByStage = targets.findAll().stream()
            .collect(Collectors.toMap(StageSlaTarget::getStage, StageSlaTarget::getTargetDays));
        long[] aging = new long[4]; // 0–15 · 16–30 · 31–60 · 60+
        for (ClientCr r : open) {
            aging[r.ageDays() <= 15 ? 0 : r.ageDays() <= 30 ? 1 : r.ageDays() <= 60 ? 2 : 3]++;
        }
        // Canonical SLA compliance — same classifier as the client
        // overview so this pillar and the overview agree for the same client.
        Integer slaCompliance = sla.compute(open, ClientCr::ageDays, r -> targetByStage.get(r.stage())).adherencePct();

        List<String> labels = new ArrayList<>();
        for (int i = 0; i < m; i++) labels.add(start.plusMonths(i).toString());

        // RAG-banded scoring vs DH_DEFS thresholds; current month = last bucket.
        Integer leadScore = leadN[m - 1] == 0 ? null : band(lead[m - 1], 15, 30, false);
        Integer cycleScore = !anyCycle || cycleN[m - 1] == 0 ? null : band(cycle[m - 1], 8, 15, false);
        Integer tputScore = band(throughput[m - 1], 20, 12, true);
        Integer slaScore = slaCompliance == null ? null : band(slaCompliance, 90, 75, true);
        Integer incScore = band(incidents[m - 1], 3, 6, false);
        // Reopened joined the quality pillar once the changelog backfill populated
        // reopen_count; a month with no completed work contributes no score.
        Integer reopenScore = throughput[m - 1] == 0 ? null : band(reopened[m - 1], 3, 8, false);
        Integer speed = avgScore(leadScore, cycleScore, tputScore, slaScore);
        Integer quality = avgScore(incScore, reopenScore);

        // Predictability from sprint metrics (F3) — null until sprints exist
        Map<String, Object> pred = velocity.predictability(id, 6);
        Integer predScore = null;
        if (Boolean.TRUE.equals(pred.get("dataAvailable"))) {
            predScore = avgScore(
                band(((Number) pred.get("commitmentPct")).doubleValue(), 85, 70, true),
                band(((Number) pred.get("spilloverPct")).doubleValue(), 10, 20, false),
                band(((Number) pred.get("scopeChangePct")).doubleValue(), 10, 20, false));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("months", labels);
        out.put("lead", lead);
        out.put("cycle", anyCycle ? cycle : null);
        out.put("throughput", throughput);
        out.put("incidents", incidents);
        out.put("reopened", reopened);
        out.put("slaCompliancePct", slaCompliance);
        out.put("predMetrics", pred);
        out.put("aging", Map.of(
            "b0_15", aging[0], "b16_30", aging[1], "b31_60", aging[2], "b60plus", aging[3],
            "total", (long) open.size()));
        Map<String, Object> pillars = new LinkedHashMap<>();
        pillars.put("speed", speed);
        pillars.put("quality", quality);
        pillars.put("pred", predScore);
        out.put("pillars", pillars);
        return out;
    }

    // ── AM widget parity (docs/plan/orbitter-am-widget-parity-plan.md) ──────

    private static final Set<String> CLOSED_STAGES =
        Set.of("released", "closed", "invalid", "resolved", "canceled", "rejected", "done");
    private static final Set<String> BACKLOG_STAGES =
        Set.of("brd awaited", "cr created", "request created", "new", "backlog", "to do", "open");

    /**
     * W11 CSAT drill — per client of the POD, work-type rows (issue type × ops
     * model) split Backlog / In Progress / Closed. Despite the mock name this
     * needs no CSAT feed; it is a pure Jira work-mix view.
     */
    @GetMapping("/csat-drill")
    public Map<String, Object> csatDrill(@RequestParam(required = false) Long portfolioId) {
        record Key(String client, Long clientId, String workType) {}
        Map<Key, long[]> buckets = new LinkedHashMap<>(); // [backlog, inProgress, closed]
        for (Object[] r : issues.findCsatDrillRows(portfolioId)) {
            String client = r[0] == null ? "Unknown" : ((String) r[0]).trim(); // live data has trailing-space dupes
            Long clientId = (Long) r[1];
            String issueType = (String) r[2];
            String ops = r[3] == null ? "" : ((String) r[3]).toLowerCase();
            String stage = r[4] == null ? "" : ((String) r[4]).trim().toLowerCase();
            long count = (Long) r[5];

            String workType = switch (issueType) {
                case "CR" -> ops.contains("launch") && !ops.contains("bau") ? "Launch · CRs" : "BAU · CRs";
                case "UAT_BUG" -> "Launch · UAT Bugs";
                case "PROD_BUG" -> "Prod Bugs";
                default -> issueType;
            };
            int bucket = CLOSED_STAGES.contains(stage) ? 2 : BACKLOG_STAGES.contains(stage) ? 0 : 1;
            buckets.computeIfAbsent(new Key(client, clientId, workType), k -> new long[3])[bucket] += count;
        }

        Map<String, Map<String, Object>> byClient = new LinkedHashMap<>();
        for (Map.Entry<Key, long[]> e : buckets.entrySet()) {
            Map<String, Object> clientRow = byClient.computeIfAbsent(e.getKey().client(), c -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("client", c);
                row.put("clientId", e.getKey().clientId());
                row.put("groups", new ArrayList<Map<String, Object>>());
                return row;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groups = (List<Map<String, Object>>) clientRow.get("groups");
            long[] b = e.getValue();
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("workType", e.getKey().workType());
            g.put("backlog", b[0]);
            g.put("inProgress", b[1]);
            g.put("closed", b[2]);
            g.put("total", b[0] + b[1] + b[2]);
            groups.add(g);
        }
        List<String> order = List.of("Launch · CRs", "Launch · UAT Bugs", "BAU · CRs", "Prod Bugs");
        for (Map<String, Object> row : byClient.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groups = (List<Map<String, Object>>) row.get("groups");
            groups.sort(Comparator.comparingInt(g -> {
                int i = order.indexOf((String) g.get("workType"));
                return i < 0 ? order.size() : i;
            }));
        }
        List<Map<String, Object>> rows = new ArrayList<>(byClient.values());
        rows.sort(Comparator.comparing(r -> (String) r.get("client")));
        return Map.of("clients", rows);
    }

    /** W5 — committed vs delivered SP for the last 6 sprints of the POD. */
    @GetMapping("/velocity")
    public Map<String, Object> podVelocity(@RequestParam(required = false) Long portfolioId,
                                           @RequestParam(defaultValue = "6") int sprints) {
        return velocity.velocityPayload(portfolioId, null, Math.min(Math.max(sprints, 1), 12));
    }

    /** W16 — milestones auto-derived from the client's sprints (zero manual input). */
    @GetMapping("/client/{id}/milestones")
    public Map<String, Object> clientMilestones(@PathVariable Long id) {
        return velocity.clientMilestones(id);
    }

    /** DH pillar weights + adoption URL (single am_settings row). */
    @GetMapping("/settings")
    public Map<String, Object> getSettings() {
        AmSettings s = settings.findById(1L).orElseGet(AmSettings::new);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dhSpeedWeight", s.getDhSpeedWeight());
        out.put("dhQualityWeight", s.getDhQualityWeight());
        out.put("dhPredWeight", s.getDhPredWeight());
        out.put("adoptionUrl", s.getAdoptionUrl());
        return out;
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> putSettings(@RequestBody Map<String, Object> body, Principal principal) {
        AmSettings s = settings.findById(1L).orElseGet(AmSettings::new);
        if (body.get("dhSpeedWeight") != null) s.setDhSpeedWeight(((Number) body.get("dhSpeedWeight")).intValue());
        if (body.get("dhQualityWeight") != null) s.setDhQualityWeight(((Number) body.get("dhQualityWeight")).intValue());
        if (body.get("dhPredWeight") != null) s.setDhPredWeight(((Number) body.get("dhPredWeight")).intValue());
        if (body.containsKey("adoptionUrl")) s.setAdoptionUrl((String) body.get("adoptionUrl"));
        int sum = s.getDhSpeedWeight() + s.getDhQualityWeight() + s.getDhPredWeight();
        if (sum != 100) throw new IllegalArgumentException("DH weights must sum to 100, got " + sum);
        s.setUpdatedBy(principal == null ? "unknown" : principal.getName());
        s.setUpdatedAt(LocalDateTime.now());
        settings.save(s);
        return getSettings();
    }

    /** RAG bands per handoff health-score v1: green 92 · amber 62 · red 32. */
    private static int band(double value, double g, double a, boolean higherIsBetter) {
        boolean green = higherIsBetter ? value >= g : value <= g;
        boolean amber = higherIsBetter ? value >= a : value <= a;
        return green ? 92 : amber ? 62 : 32;
    }

    private static Integer avgScore(Integer... scores) {
        List<Integer> present = Arrays.stream(scores).filter(Objects::nonNull).toList();
        if (present.isEmpty()) return null;
        return (int) Math.round(present.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private static Long medianAge(List<CrRow> rows) {
        if (rows.isEmpty()) return 0L;
        long[] ages = rows.stream().mapToLong(CrRow::ageDays).sorted().toArray();
        return ages[ages.length / 2];
    }

    private static Map<String, Object> toDrillRow(JiraIssue j, LocalDateTime now) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", j.getIssueKey());
        row.put("client", j.getClient() != null ? j.getClient().getName() : null);
        row.put("summary", j.getSummary());
        row.put("status", j.getJiraStatus());
        row.put("stage", j.getLifecycleStage());
        row.put("owner", j.getAssigneeName());
        row.put("type", j.getProject() != null ? j.getProject().getOpsModel() : null);
        row.put("agingDays", j.getCreatedAt() == null ? 0 : ChronoUnit.DAYS.between(j.getCreatedAt(), now));
        return row;
    }
}
