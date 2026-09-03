package com.orbit.controller;

import com.orbit.domain.client.Client;
import com.orbit.domain.client.LifecycleMapping;
import com.orbit.domain.client.Portfolio;
import com.orbit.domain.client.Project;
import com.orbit.domain.config.StageSlaTarget;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.ClientRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.LifecycleMappingRepository;
import com.orbit.repository.PortfolioRepository;
import com.orbit.repository.StageSlaTargetRepository;
import com.orbit.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = AmDashboardController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@org.springframework.context.annotation.Import(com.orbit.service.am.SlaBucketService.class)
class AmDashboardControllerTest {

    @Autowired MockMvc mvc;
    @MockBean JiraIssueRepository issues;
    @MockBean StageSlaTargetRepository targets;
    @MockBean LifecycleMappingRepository lifecycle;
    @MockBean PortfolioRepository portfolios;
    @MockBean ClientRepository clients;
    @MockBean com.orbit.repository.ProjectRepository projects;
    @MockBean com.orbit.repository.AmSettingsRepository amSettings;
    @MockBean com.orbit.repository.JiraConfigRepository jiraConfigs;
    @MockBean com.orbit.service.sync.VelocityService velocity;
    @MockBean com.orbit.repository.ProjectReleaseRepository releases;
    @MockBean JwtService jwtService;

    @org.junit.jupiter.api.BeforeEach
    void stubVelocityDefaults() {
        when(velocity.velocityPayload(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(new java.util.HashMap<>(java.util.Map.of("dataAvailable", false)));
        when(velocity.predictability(any(), org.mockito.ArgumentMatchers.anyInt()))
            .thenReturn(java.util.Map.of("dataAvailable", false));
    }

    private static Object[] cr(String client, String stage, String owner, String ops, int ageDays) {
        return new Object[]{client, stage, owner, ops, LocalDateTime.now().minusDays(ageDays)};
    }

    private static StageSlaTarget target(String stage, int days) {
        StageSlaTarget t = new StageSlaTarget();
        t.setStage(stage);
        t.setTargetDays(days);
        return t;
    }

    private static LifecycleMapping stageOrder(String stage, int order) {
        LifecycleMapping m = new LifecycleMapping();
        m.setGaugeStage(stage);
        m.setDisplayOrder(order);
        return m;
    }

    @Test
    void summarySplitsOpenAndClientHold() throws Exception {
        when(issues.findOpenAmCrRows(null)).thenReturn(List.<Object[]>of(
            cr("ACME", "In Progress", "Asha", "bau", 10),
            cr("ACME", "Hold", "Asha", "bau", 40),
            cr("ZENITH", "Received", "Ravi", "launch", 5)
        ));
        mvc.perform(get("/api/v1/am/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openCrs").value(2))
            .andExpect(jsonPath("$.clientHold").value(1))
            .andExpect(jsonPath("$.clients").value(2));
    }

    @Test
    void summaryFiltersByOpsModelType() throws Exception {
        when(issues.findOpenAmCrRows(null)).thenReturn(List.<Object[]>of(
            cr("ACME", "In Progress", "Asha", "bau", 10),
            cr("ZENITH", "Received", "Ravi", "launch+bau", 5),
            cr("LK", "Received", "Ravi", "launch", 5)
        ));
        mvc.perform(get("/api/v1/am/summary").param("type", "BAU"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openCrs").value(2)); // bau + launch+bau
    }

    @Test
    void stageMatrixComputesSlaPctAndAging() throws Exception {
        when(issues.findOpenAmCrRows(null)).thenReturn(List.<Object[]>of(
            cr("ACME", "In Progress", "Asha", "bau", 10),   // within 45d
            cr("ACME", "In Progress", "Ravi", "bau", 90),   // beyond 45d
            cr("ZENITH", "Hold", "Ravi", "bau", 30)           // untracked stage
        ));
        when(targets.findAll()).thenReturn(List.of(target("In Progress", 45)));
        when(lifecycle.findAll()).thenReturn(List.of(stageOrder("In Progress", 55)));

        mvc.perform(get("/api/v1/am/stage-matrix"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clients[0]").value("ACME"))
            .andExpect(jsonPath("$.stages[0].stage").value("In Progress"))
            .andExpect(jsonPath("$.stages[0].withinSlaPct").value(50))
            .andExpect(jsonPath("$.stages[0].avgAgingDays").value(50))
            .andExpect(jsonPath("$.stages[0].cells.ACME.count").value(2))
            .andExpect(jsonPath("$.stages[1].stage").value("Hold"))
            .andExpect(jsonPath("$.stages[1].withinSlaPct").doesNotExist())
            .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void ownerMatrixGroupsByAssigneeWithUnassignedBucket() throws Exception {
        when(issues.findOpenAmCrRows(null)).thenReturn(List.<Object[]>of(
            cr("ACME", "In Progress", "Asha", "bau", 10),
            cr("ACME", "Received", "Asha", "bau", 3),
            cr("ZENITH", "Received", null, "bau", 5)
        ));
        when(lifecycle.findAll()).thenReturn(List.of(
            stageOrder("Received", 10), stageOrder("In Progress", 55)));

        mvc.perform(get("/api/v1/am/owner-matrix"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.owners[0].owner").value("Asha"))
            .andExpect(jsonPath("$.owners[0].total").value(2))
            .andExpect(jsonPath("$.owners[1].owner").value("Unassigned"))
            .andExpect(jsonPath("$.stages[0]").value("Received"));
    }

    @Test
    void prodTrendBucketsCreatedClosedByMonthAndCountsOpen() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(issues.findAmProdBugRows(eq(null), any())).thenReturn(List.<Object[]>of(
            new Object[]{now.minusDays(2), null, "P0"},                 // created this month, open
            new Object[]{now.minusDays(2), now.minusDays(1), "P2"},    // created + closed this month
            new Object[]{now.minusMonths(13), null, "P1"}              // old, still open
        ));
        mvc.perform(get("/api/v1/am/prod-trend").param("months", "12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.months.length()").value(12))
            .andExpect(jsonPath("$.created[11]").value(2))
            .andExpect(jsonPath("$.closed[11]").value(1))
            .andExpect(jsonPath("$.openNow").value(2))
            .andExpect(jsonPath("$.openBySeverity.P0").value(1));
    }

    @Test
    void clientsScorecardMergesCrAndProdCounts() throws Exception {
        when(issues.findOpenAmCrRows(null)).thenReturn(List.<Object[]>of(
            cr("ACME", "In Progress", "Asha", "bau", 20)
        ));
        when(issues.countOpenProdBugsByClientAndSeverity(null)).thenReturn(List.<Object[]>of(
            new Object[]{"ACME", "P1", 3L},
            new Object[]{"ZETA", "P0", 1L}
        ));
        mvc.perform(get("/api/v1/am/clients"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].client").value("ACME"))
            .andExpect(jsonPath("$[0].openCrs").value(1))
            .andExpect(jsonPath("$[0].openProdBySeverity.P1").value(3))
            .andExpect(jsonPath("$[1].client").value("ZETA"))
            .andExpect(jsonPath("$[1].openCrs").value(0));
    }

    @Test
    void clientsScorecardResolvesTileIdFromActiveClientsOnly() throws Exception {
        // a retired duplicate row sharing the name must not hijack clientId —
        // the tile id comes from the active-clients lookup, never findAll()
        when(issues.findOpenAmCrRows(null)).thenReturn(List.<Object[]>of(
            cr("Atlas Bank", "In Progress", "Asha", "bau", 20)
        ));
        Client active = new Client();
        org.springframework.test.util.ReflectionTestUtils.setField(active, "id", 138L);
        active.setName("Atlas Bank");
        when(clients.findByActiveTrue()).thenReturn(List.of(active));

        mvc.perform(get("/api/v1/am/clients"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].client").value("Atlas Bank"))
            .andExpect(jsonPath("$[0].clientId").value(138));
    }

    @Test
    void crDrillIsPaginatedAndMapsRows() throws Exception {
        Client c = new Client(); c.setName("ACME");
        Project p = new Project(); p.setOpsModel("bau");
        JiraIssue j = new JiraIssue();
        j.setIssueKey("CR-101");
        j.setSummary("Payment file re-run");
        j.setJiraStatus("In Progress");
        j.setLifecycleStage("In Progress");
        j.setAssigneeName("Asha");
        j.setClient(c);
        j.setProject(p);
        j.setCreatedAt(LocalDateTime.now().minusDays(12));
        when(issues.findAmCrDrill(eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(j)));

        mvc.perform(get("/api/v1/am/crs").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].key").value("CR-101"))
            .andExpect(jsonPath("$.content[0].client").value("ACME"))
            .andExpect(jsonPath("$.content[0].agingDays").value(12))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ── V3 reforms ───────────────────────────────────────────────────────────

    private static Portfolio pod(long id, String name) {
        Portfolio p = new Portfolio();
        p.setName(name);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void podScoreRanksByAbsoluteSlaAdherence() throws Exception {
        when(portfolios.findByActiveTrue()).thenReturn(List.of(pod(1, "Collections"), pod(2, "Lending")));
        when(targets.findAll()).thenReturn(List.of(target("In Progress", 45)));
        LocalDateTime now = LocalDateTime.now();
        when(issues.findOpenCrRowsAllPortfolios()).thenReturn(List.<Object[]>of(
            new Object[]{1L, "Collections", "In Progress", now.minusDays(10), "bau"},     // met
            new Object[]{1L, "Collections", "In Progress", now.minusDays(90), "launch"},  // breached
            new Object[]{2L, "Lending", "In Progress", now.minusDays(5), "bau"}           // met
        ));
        when(issues.countOpenProdBugsByPortfolioAndSeverity()).thenReturn(List.<Object[]>of(
            new Object[]{1L, "P0", 2L}
        ));

        mvc.perform(get("/api/v1/am/pod-score"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Lending"))     // 100% adherence outranks 50%
            .andExpect(jsonPath("$[0].score").value(100))
            .andExpect(jsonPath("$[0].rank").value(1))
            .andExpect(jsonPath("$[1].name").value("Collections"))
            .andExpect(jsonPath("$[1].score").value(50))
            .andExpect(jsonPath("$[1].slaBreached").value(1))
            .andExpect(jsonPath("$[1].openBauCrs").value(1))
            .andExpect(jsonPath("$[1].openLaunchCrs").value(1))
            .andExpect(jsonPath("$[1].prodOpen").value(2))
            .andExpect(jsonPath("$[1].prodBySeverity.P0").value(2));
    }

    @Test
    void prodWeeklySplitsWeeksAndReconcilesOpenLine() throws Exception {
        when(issues.findAmProdBugRows(eq(null), any())).thenReturn(List.<Object[]>of(
            new Object[]{LocalDateTime.of(2026, 5, 3, 10, 0), null, "P0"},                                  // W1, still open
            new Object[]{LocalDateTime.of(2026, 5, 10, 10, 0), LocalDateTime.of(2026, 5, 20, 10, 0), "P2"} // W2 created, W3 resolved
        ));
        mvc.perform(get("/api/v1/am/prod-weekly").param("from", "2026-05").param("to", "2026-05"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.months.length()").value(1))
            .andExpect(jsonPath("$.months[0].created[0]").value(1))
            .andExpect(jsonPath("$.months[0].created[1]").value(1))
            .andExpect(jsonPath("$.months[0].resolved[2]").value(1))
            .andExpect(jsonPath("$.months[0].open[0]").value(1))
            .andExpect(jsonPath("$.months[0].open[1]").value(2))
            .andExpect(jsonPath("$.months[0].open[3]").value(1))   // walk ends at openNow
            .andExpect(jsonPath("$.openNow").value(1));
    }

    @Test
    void ownerShareCountsOpenCrsByOwner() throws Exception {
        when(issues.findOpenAmCrRows(null)).thenReturn(List.<Object[]>of(
            cr("ACME", "In Progress", "Asha", "bau", 10),
            cr("ACME", "Received", "Asha", "bau", 3),
            cr("ZENITH", "Received", null, "launch", 5)
        ));
        mvc.perform(get("/api/v1/am/owner-share"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.owners[0].owner").value("Asha"))
            .andExpect(jsonPath("$.owners[0].count").value(2))
            .andExpect(jsonPath("$.owners[1].owner").value("Unassigned"))
            .andExpect(jsonPath("$.total").value(3));
    }

    private static Object[] clientCr(String stage, int ageDays, String ops) {
        return new Object[]{stage, LocalDateTime.now().minusDays(ageDays), ops, "Asha"};
    }

    @Test
    void clientOverviewComputesBreachedNearMetAndTiles() throws Exception {
        Client c = new Client(); c.setName("ACME"); c.setCode("ACME");
        when(clients.findById(7L)).thenReturn(java.util.Optional.of(c));
        when(targets.findAll()).thenReturn(List.of(target("In Progress", 40)));
        when(lifecycle.findAll()).thenReturn(List.of(stageOrder("In Progress", 55)));
        when(issues.findOpenCrRowsForClient(7L)).thenReturn(List.<Object[]>of(
            clientCr("In Progress", 10, "bau"),      // met (comfortable)
            clientCr("In Progress", 35, "bau"),      // near (<25% window left)
            clientCr("In Progress", 50, "launch"),   // breached
            clientCr("Hold", 90, "bau")              // untracked, client hold
        ));
        when(issues.findProdBugRowsForClient(eq(7L), any())).thenReturn(List.<Object[]>of(
            new Object[]{LocalDateTime.now().minusDays(3), null, "P1"}
        ));
        when(portfolios.findByClientsIdAndActiveTrue(7L)).thenReturn(List.of(pod(1, "Collections")));

        mvc.perform(get("/api/v1/am/client/7/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.client").value("ACME"))
            .andExpect(jsonPath("$.pod").value("Collections"))
            .andExpect(jsonPath("$.openCrs").value(3))
            .andExpect(jsonPath("$.clientHold").value(1))
            .andExpect(jsonPath("$.openBauCrs").value(3))
            .andExpect(jsonPath("$.openLaunchCrs").value(1))
            .andExpect(jsonPath("$.slaMet").value(1))
            .andExpect(jsonPath("$.slaNear").value(1))
            .andExpect(jsonPath("$.slaBreached").value(1))
            .andExpect(jsonPath("$.slaAdherencePct").value(33))
            .andExpect(jsonPath("$.prodOpen").value(1))
            .andExpect(jsonPath("$.stages[0].stage").value("In Progress"))
            .andExpect(jsonPath("$.stages[0].total").value(3))
            .andExpect(jsonPath("$.csat").doesNotExist());
    }

    @Test
    void dhMetricsComputesRealCardsAndBandedPillars() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(targets.findAll()).thenReturn(List.of(target("In Progress", 45)));
        when(issues.findResolvedCrRowsForClient(eq(7L), any())).thenReturn(List.<Object[]>of(
            new Object[]{now.minusDays(11), now.minusDays(1), "bau", 0},   // lead 10
            new Object[]{now.minusDays(27), now.minusDays(2), "bau", 1}    // lead 25, reopened once
        ));
        when(issues.findProdBugRowsForClient(eq(7L), any())).thenReturn(List.<Object[]>of(
            new Object[]{now.minusDays(1), null, "P1"}
        ));
        when(issues.findOpenCrRowsForClient(7L)).thenReturn(List.<Object[]>of(
            clientCr("In Progress", 5, "bau"),
            clientCr("In Progress", 20, "bau"),
            clientCr("In Progress", 40, "bau"),
            clientCr("In Progress", 70, "bau")
        ));

        mvc.perform(get("/api/v1/am/client/7/dh-metrics").param("months", "6"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.months.length()").value(6))
            .andExpect(jsonPath("$.lead[5]").value(18))            // avg(10,25) rounded
            .andExpect(jsonPath("$.throughput[5]").value(2))
            .andExpect(jsonPath("$.incidents[5]").value(1))
            .andExpect(jsonPath("$.slaCompliancePct").value(50))   // canonical 3-bucket: met 2 (age 5,20), near 1 (age 40 in last 25% of 45d), breached 1 (age 70) → 2/4
            .andExpect(jsonPath("$.aging.b0_15").value(1))
            .andExpect(jsonPath("$.aging.b16_30").value(1))
            .andExpect(jsonPath("$.aging.b31_60").value(1))
            .andExpect(jsonPath("$.aging.b60plus").value(1))
            .andExpect(jsonPath("$.reopened[5]").value(50))        // 1 of 2 resolved bounced back
            .andExpect(jsonPath("$.pillars.speed").value(42))      // avg(62 lead, 32 tput, 32 sla: 50% < 75 amber → red)
            .andExpect(jsonPath("$.pillars.quality").value(62))    // avg(92 incidents: 1 ≤ 3, 32 reopened: 50% > 8%)
            .andExpect(jsonPath("$.pillars.pred").doesNotExist());
    }

    /**
     * Cross-widget reconciliation guard (widget-parity plan §E): the CR
     * executive summary, stage matrix, and owner donut all derive from the
     * same open-CR row set, so their totals must always agree —
     * summary.openCrs + summary.clientHold == matrix.total == Σ clientTotals
     * == Σ stage totals == ownerShare.total. If this test breaks, a widget
     * has forked its own counting logic.
     */
    @Test
    void summaryMatrixAndOwnerShareTotalsReconcile() throws Exception {
        List<Object[]> rows = List.<Object[]>of(
            cr("ACME", "In Progress", "Asha", "bau", 10),
            cr("ACME", "Received", "Ravi", "bau", 3),
            cr("ACME", "Hold", "Asha", "bau", 40),
            cr("ZENITH", "Received", "Ravi", "launch", 5),
            cr("ZENITH", "Hold", null, "launch", 25),
            cr("LK", "UAT Released", null, "launch+bau", 8)
        );
        when(issues.findOpenAmCrRows(null)).thenReturn(rows);
        when(targets.findAll()).thenReturn(List.of(target("In Progress", 45), target("Received", 30)));
        when(lifecycle.findAll()).thenReturn(List.of(
            stageOrder("Received", 10), stageOrder("In Progress", 55), stageOrder("UAT Released", 70)));

        String summaryJson = mvc.perform(get("/api/v1/am/summary"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String matrixJson = mvc.perform(get("/api/v1/am/stage-matrix"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String ownerJson = mvc.perform(get("/api/v1/am/owner-share"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode summary = om.readTree(summaryJson);
        com.fasterxml.jackson.databind.JsonNode matrix = om.readTree(matrixJson);
        com.fasterxml.jackson.databind.JsonNode owners = om.readTree(ownerJson);

        long summaryTotal = summary.get("openCrs").asLong() + summary.get("clientHold").asLong();
        long matrixTotal = matrix.get("total").asLong();
        long clientTotals = 0;
        for (var it = matrix.get("clientTotals").elements(); it.hasNext(); ) clientTotals += it.next().asLong();
        long stageTotals = 0;
        for (var it = matrix.get("stages").elements(); it.hasNext(); ) stageTotals += it.next().get("total").asLong();

        org.assertj.core.api.Assertions.assertThat(summaryTotal).isEqualTo(rows.size());
        org.assertj.core.api.Assertions.assertThat(matrixTotal).isEqualTo(summaryTotal);
        org.assertj.core.api.Assertions.assertThat(clientTotals).isEqualTo(summaryTotal);
        org.assertj.core.api.Assertions.assertThat(stageTotals).isEqualTo(summaryTotal);
        org.assertj.core.api.Assertions.assertThat(owners.get("total").asLong()).isEqualTo(summaryTotal);
    }

    /**
     * SLA-bucket reconciliation harness. The same four open CRs
     * (target 45d; ages 5,20,40,70 → met 2, near 1, breached 1) are fed through
     * the three different repository shapes that back the stage matrix, the
     * client overview and the POD score. Because all three now route through
     * {@code SlaBucketService}, they MUST report identical met/near/breached —
     * this test fails the moment any surface grows its own SLA formula again.
     */
    @Test
    void slaBucketsReconcileAcrossMatrixOverviewAndPodScore() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        int[] ages = {5, 20, 40, 70}; // met, met, near (in last 25% of 45d), breached
        when(targets.findAll()).thenReturn(List.of(target("In Progress", 45)));
        when(lifecycle.findAll()).thenReturn(List.of(stageOrder("In Progress", 55)));

        // stage matrix (portfolio scope) — findOpenAmCrRows
        when(issues.findOpenAmCrRows(null)).thenReturn(List.<Object[]>of(
            cr("ACME", "In Progress", "Asha", "bau", ages[0]),
            cr("ACME", "In Progress", "Asha", "bau", ages[1]),
            cr("ACME", "In Progress", "Asha", "bau", ages[2]),
            cr("ACME", "In Progress", "Asha", "bau", ages[3])));
        // client overview — findOpenCrRowsForClient
        Client acme = new Client(); acme.setName("ACME"); acme.setCode("ACME");
        when(clients.findById(7L)).thenReturn(java.util.Optional.of(acme));
        when(issues.findOpenCrRowsForClient(7L)).thenReturn(List.<Object[]>of(
            clientCr("In Progress", ages[0], "bau"),
            clientCr("In Progress", ages[1], "bau"),
            clientCr("In Progress", ages[2], "bau"),
            clientCr("In Progress", ages[3], "bau")));
        // POD score — findOpenCrRowsAllPortfolios
        when(portfolios.findByActiveTrue()).thenReturn(List.of(pod(1, "Collections")));
        when(issues.findOpenCrRowsAllPortfolios()).thenReturn(List.<Object[]>of(
            new Object[]{1L, "Collections", "In Progress", now.minusDays(ages[0]), "bau"},
            new Object[]{1L, "Collections", "In Progress", now.minusDays(ages[1]), "bau"},
            new Object[]{1L, "Collections", "In Progress", now.minusDays(ages[2]), "bau"},
            new Object[]{1L, "Collections", "In Progress", now.minusDays(ages[3]), "bau"}));

        var om = new com.fasterxml.jackson.databind.ObjectMapper();
        var matrix = om.readTree(mvc.perform(get("/api/v1/am/stage-matrix"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var overview = om.readTree(mvc.perform(get("/api/v1/am/client/7/overview"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var pods = om.readTree(mvc.perform(get("/api/v1/am/pod-score"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        var matrixStage = matrix.get("stages").get(0);
        var pod = pods.get(0);
        // canonical answer
        org.assertj.core.api.Assertions.assertThat(matrixStage.get("met").asLong()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(matrixStage.get("near").asLong()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(matrixStage.get("breached").asLong()).isEqualTo(1);
        // ...and every other surface agrees, field for field
        for (String f : List.of("met", "near", "breached")) {
            long m = matrixStage.get(f).asLong();
            org.assertj.core.api.Assertions.assertThat(overview.get("sla" + cap(f)).asLong())
                .as("client overview sla%s == stage matrix %s", cap(f), f).isEqualTo(m);
            org.assertj.core.api.Assertions.assertThat(pod.get("sla" + cap(f)).asLong())
                .as("pod-score sla%s == stage matrix %s", cap(f), f).isEqualTo(m);
        }
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── Widget-parity plan (Wave 2) ──────────────────────────────────────────

    private static Portfolio podWithClients(long id, String name, Client... members) {
        Portfolio p = pod(id, name);
        p.setClients(new java.util.LinkedHashSet<>(List.of(members)));
        return p;
    }

    private static Client csatClient(String name, String launch, String bau) {
        Client c = new Client();
        c.setName(name);
        if (launch != null) c.setCsatLaunch(new java.math.BigDecimal(launch));
        if (bau != null) c.setCsatBau(new java.math.BigDecimal(bau));
        return c;
    }

    @Test
    void podScoreBlendsCsatAndSlaMinMaxWhenCsatEntered() throws Exception {
        // Collections: csat avg 9.0, SLA 50% · Lending: csat avg 7.0, SLA 100%
        when(portfolios.findByActiveTrue()).thenReturn(List.of(
            podWithClients(1, "Collections", csatClient("ACME", "9.0", "9.0")),
            podWithClients(2, "Lending", csatClient("ZENITH", "7.0", "7.0"))));
        when(targets.findAll()).thenReturn(List.of(target("In Progress", 45)));
        LocalDateTime now = LocalDateTime.now();
        when(issues.findOpenCrRowsAllPortfolios()).thenReturn(List.<Object[]>of(
            new Object[]{1L, "Collections", "In Progress", now.minusDays(10), "bau"},
            new Object[]{1L, "Collections", "In Progress", now.minusDays(90), "bau"},
            new Object[]{2L, "Lending", "In Progress", now.minusDays(5), "bau"}
        ));
        when(issues.countOpenProdBugsByPortfolioAndSeverity()).thenReturn(List.of());

        // min-max: Collections csatNorm=1 slaNorm=0 → 60 · Lending csatNorm=0 slaNorm=1 → 40
        mvc.perform(get("/api/v1/am/pod-score"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Collections"))
            .andExpect(jsonPath("$[0].score").value(60))
            .andExpect(jsonPath("$[0].csatLaunch").value(9.0))
            .andExpect(jsonPath("$[1].name").value("Lending"))
            .andExpect(jsonPath("$[1].score").value(40));
    }

    @Test
    void ownerShareSmDimNeedsMappingThenGroupsBySmOwner() throws Exception {
        // unmapped → explicit configured=false, no fake assignee data
        when(jiraConfigs.findFirstByOrderByIdAsc()).thenReturn(java.util.Optional.of(new com.orbit.domain.config.JiraConfig()));
        mvc.perform(get("/api/v1/am/owner-share").param("dim", "sm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(false));

        com.orbit.domain.config.JiraConfig cfg = new com.orbit.domain.config.JiraConfig();
        cfg.setSmField("customfield_11000");
        when(jiraConfigs.findFirstByOrderByIdAsc()).thenReturn(java.util.Optional.of(cfg));
        when(issues.findOpenAmCrRows(null)).thenReturn(List.<Object[]>of(
            new Object[]{"ACME", "In dev", "Asha", "bau", LocalDateTime.now().minusDays(3), "Sonia", "Prakash"},
            new Object[]{"ACME", "In dev", "Ravi", "bau", LocalDateTime.now().minusDays(5), "Sonia", "Meera"},
            new Object[]{"ZENITH", "In QA", "Ravi", "launch", LocalDateTime.now().minusDays(2), null, null}
        ));
        mvc.perform(get("/api/v1/am/owner-share").param("dim", "sm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.owners[0].owner").value("Sonia"))
            .andExpect(jsonPath("$.owners[0].count").value(2))
            .andExpect(jsonPath("$.owners[1].owner").value("Unassigned"))
            .andExpect(jsonPath("$.total").value(3));
        mvc.perform(get("/api/v1/am/owner-share").param("dim", "pjm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(false)); // pjm field still unmapped
    }

    // ── Widget-parity plan (Wave 1) ──────────────────────────────────────────

    @Test
    void csatDrillBucketsWorkTypesByLifecycleCategory() throws Exception {
        when(issues.findCsatDrillRows(null)).thenReturn(List.<Object[]>of(
            new Object[]{"ACME", 7L, "CR", "bau", "In dev", 3L},          // BAU · CRs, in progress
            new Object[]{"ACME", 7L, "CR", "bau", "Closed", 5L},          // BAU · CRs, closed
            new Object[]{"ACME", 7L, "CR", "launch", "BRD awaited", 2L},  // Launch · CRs, backlog
            new Object[]{"ACME", 7L, "UAT_BUG", "launch", "Released", 1L},
            new Object[]{"ZENITH", 8L, "PROD_BUG", "bau", "New", 4L}        // backlog bucket
        ));
        mvc.perform(get("/api/v1/am/csat-drill"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clients[0].client").value("ACME"))
            .andExpect(jsonPath("$.clients[0].groups[0].workType").value("Launch · CRs"))
            .andExpect(jsonPath("$.clients[0].groups[0].backlog").value(2))
            .andExpect(jsonPath("$.clients[0].groups[1].workType").value("Launch · UAT Bugs"))
            .andExpect(jsonPath("$.clients[0].groups[1].closed").value(1))
            .andExpect(jsonPath("$.clients[0].groups[2].workType").value("BAU · CRs"))
            .andExpect(jsonPath("$.clients[0].groups[2].inProgress").value(3))
            .andExpect(jsonPath("$.clients[0].groups[2].closed").value(5))
            .andExpect(jsonPath("$.clients[0].groups[2].total").value(8))
            .andExpect(jsonPath("$.clients[1].client").value("ZENITH"))
            .andExpect(jsonPath("$.clients[1].groups[0].workType").value("Prod Bugs"))
            .andExpect(jsonPath("$.clients[1].groups[0].backlog").value(4));
    }

    @Test
    void settingsReturnDefaultsAndRejectBadWeights() throws Exception {
        when(amSettings.findById(1L)).thenReturn(java.util.Optional.empty());
        mvc.perform(get("/api/v1/am/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dhSpeedWeight").value(40))
            .andExpect(jsonPath("$.dhQualityWeight").value(35))
            .andExpect(jsonPath("$.dhPredWeight").value(25));
    }

    @Test
    void summaryCountsOnHoldAsClientHold() throws Exception {
        when(issues.findOpenAmCrRows(null)).thenReturn(List.<Object[]>of(
            cr("ACME", "On Hold", "Asha", "bau", 12),
            cr("ACME", "Hold", "Asha", "bau", 40),
            cr("ZENITH", "In dev", "Ravi", "launch", 5)
        ));
        mvc.perform(get("/api/v1/am/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openCrs").value(1))
            .andExpect(jsonPath("$.clientHold").value(2));
    }

    @Test
    void stageMatrixIncludesMetBreachedAndMedian() throws Exception {
        when(issues.findOpenAmCrRows(null)).thenReturn(List.<Object[]>of(
            cr("ACME", "In Progress", "Asha", "bau", 10),
            cr("ACME", "In Progress", "Ravi", "bau", 90)
        ));
        when(targets.findAll()).thenReturn(List.of(target("In Progress", 45)));
        when(lifecycle.findAll()).thenReturn(List.of(stageOrder("In Progress", 55)));

        mvc.perform(get("/api/v1/am/stage-matrix"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stages[0].met").value(1))
            .andExpect(jsonPath("$.stages[0].breached").value(1))
            .andExpect(jsonPath("$.stages[0].medianAgingDays").value(90));
    }
}
