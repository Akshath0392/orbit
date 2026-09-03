package com.orbit.service.sync;

import com.orbit.domain.config.JiraSyncRun;
import com.orbit.repository.*;
import com.orbit.service.SlaService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * The 30-min scheduled delta must not sync concurrently with an in-flight
 * Full/Delta run — it records a visible 'Skipped' row instead.
 */
class JiraSyncServiceSchedulerGuardTest {

    private static JiraSyncRun run(String type, LocalDateTime startedAt) {
        JiraSyncRun r = new JiraSyncRun();
        r.setSyncType(type);
        r.setStatus("Running");
        r.setStartedAt(startedAt);
        return r;
    }

    // ── blocksScheduledDelta predicate ───────────────────────────────────────

    @Test
    void freshRunningFullSyncBlocks() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(JiraSyncService.blocksScheduledDelta(run("Full", now.minusMinutes(10)), now)).isTrue();
    }

    @Test
    void freshRunningDeltaBlocks() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(JiraSyncService.blocksScheduledDelta(run("Delta", now.minusMinutes(5)), now)).isTrue();
    }

    @Test
    void nonIssueSyncTypesDoNotBlock() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(JiraSyncService.blocksScheduledDelta(run("Webhook", now.minusMinutes(1)), now)).isFalse();
        assertThat(JiraSyncService.blocksScheduledDelta(run("ChangelogBackfill", now.minusMinutes(1)), now)).isFalse();
    }

    @Test
    void staleRunningRowDoesNotBlock() {
        // a crashed JVM leaves its row Running forever — must not silence the scheduler
        LocalDateTime now = LocalDateTime.now();
        assertThat(JiraSyncService.blocksScheduledDelta(run("Full", now.minusHours(3)), now)).isFalse();
    }

    @Test
    void nullStartedAtDoesNotBlock() {
        assertThat(JiraSyncService.blocksScheduledDelta(run("Full", null), LocalDateTime.now())).isFalse();
    }

    // ── withoutOrderBy (approximate-count rejects ORDER BY) ──────────────────

    @Test
    void withoutOrderByStripsTrailingOrderByCaseInsensitively() {
        assertThat(JiraSyncService.withoutOrderBy("project in (NX) ORDER BY created DESC"))
            .isEqualTo("project in (NX)");
        assertThat(JiraSyncService.withoutOrderBy("project = NX and updated >= -1d order by updated desc"))
            .isEqualTo("project = NX and updated >= -1d");
        assertThat(JiraSyncService.withoutOrderBy("project = NX")).isEqualTo("project = NX");
        assertThat(JiraSyncService.withoutOrderBy(null)).isNull();
    }

    // ── scheduledDeltaSync behavior ──────────────────────────────────────────

    private JiraSyncService serviceWith(JiraSyncRunRepository syncRuns, JiraConfigRepository configs) {
        JiraSyncService svc = new JiraSyncService(configs,
            mock(ProjectRepository.class), mock(JiraIssueRepository.class), syncRuns,
            mock(LifecycleMappingRepository.class), mock(SlaService.class),
            mock(ProdBugRoutingService.class), mock(SprintIngestService.class),
            mock(org.springframework.context.ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(svc, "deltaSyncEnabled", true);
        return svc;
    }

    @Test
    void tickRecordsSkippedRow_whenFullSyncInFlight() {
        JiraSyncRunRepository syncRuns = mock(JiraSyncRunRepository.class);
        JiraConfigRepository configs = mock(JiraConfigRepository.class);
        JiraSyncRun inFlight = run("Full", LocalDateTime.now().minusMinutes(3));
        ReflectionTestUtils.setField(inFlight, "id", 389L);
        inFlight.setTotalExpected(8524);
        inFlight.setProcessedSoFar(5150);
        when(syncRuns.findByStatus("Running")).thenReturn(List.of(inFlight));

        serviceWith(syncRuns, configs).scheduledDeltaSync();

        ArgumentCaptor<JiraSyncRun> saved = ArgumentCaptor.forClass(JiraSyncRun.class);
        verify(syncRuns).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("Skipped");
        assertThat(saved.getValue().getSyncType()).isEqualTo("Delta");
        assertThat(saved.getValue().getTriggeredBy()).isEqualTo("scheduler");
        assertThat(saved.getValue().getDurationMs()).isZero();
        assertThat(saved.getValue().getErrorMessage())
            .contains("Full sync run #389").contains("5,150").contains("8,524");
        // the actual sync never started — Jira config was never consulted
        verify(configs, never()).findFirstByOrderByIdAsc();
    }

    @Test
    void tickProceeds_whenNoBlockingRun() {
        JiraSyncRunRepository syncRuns = mock(JiraSyncRunRepository.class);
        JiraConfigRepository configs = mock(JiraConfigRepository.class);
        when(syncRuns.findByStatus("Running")).thenReturn(List.of());
        when(configs.findFirstByOrderByIdAsc()).thenReturn(Optional.empty()); // unconfigured → trigger no-ops

        serviceWith(syncRuns, configs).scheduledDeltaSync();

        verify(configs).findFirstByOrderByIdAsc(); // trigger path was entered
        verify(syncRuns, never()).save(any());     // and no Skipped row written
    }

    @Test
    void tickDoesNothing_whenDeltaSyncDisabled() {
        JiraSyncRunRepository syncRuns = mock(JiraSyncRunRepository.class);
        JiraConfigRepository configs = mock(JiraConfigRepository.class);
        JiraSyncService svc = serviceWith(syncRuns, configs);
        ReflectionTestUtils.setField(svc, "deltaSyncEnabled", false);

        svc.scheduledDeltaSync();

        verifyNoInteractions(syncRuns, configs);
    }
}
