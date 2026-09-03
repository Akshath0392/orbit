package com.orbit.unit;

import com.orbit.domain.config.Stage;
import com.orbit.domain.config.StageSlaTarget;
import com.orbit.repository.*;
import com.orbit.service.StageCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StageCatalogServiceTest {

    @Mock StageRepository stages;
    @Mock LifecycleMappingRepository mappings;
    @Mock JiraIssueRepository issues;
    @Mock StageSlaTargetRepository slaTargets;

    StageCatalogService service;

    @BeforeEach
    void setUp() {
        service = new StageCatalogService(stages, mappings, issues, slaTargets);
    }

    private Stage stage(long id, String name) {
        Stage s = new Stage();
        org.springframework.test.util.ReflectionTestUtils.setField(s, "id", id);
        s.setName(name);
        return s;
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void createRejectsDuplicateNameCaseInsensitive() {
        when(stages.findByNameIgnoreCase("In Dev")).thenReturn(Optional.of(stage(1, "In dev")));

        assertThatThrownBy(() -> service.create("In Dev", null, null, "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(stages, never()).save(any());
    }

    @Test
    void createDefaultsOrderToMaxPlusTen() {
        when(stages.findByNameIgnoreCase("Pilot")).thenReturn(Optional.empty());
        when(stages.maxDisplayOrder()).thenReturn(100);
        when(stages.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Stage s = service.create("  Pilot  ", "uat", null, "admin");

        assertThat(s.getName()).isEqualTo("Pilot");
        assertThat(s.getDisplayOrder()).isEqualTo(110);
        assertThat(s.getCategory()).isEqualTo("uat");
    }

    // ── rename cascade ────────────────────────────────────────────────────────

    @Test
    void renameCascadesToAllDenormalizedTables() {
        Stage s = stage(5, "In dev");
        when(stages.findById(5L)).thenReturn(Optional.of(s));
        when(stages.findByNameIgnoreCase("In development")).thenReturn(Optional.empty());
        StageSlaTarget target = new StageSlaTarget();
        target.setStage("In dev");
        when(slaTargets.findByStage("In dev")).thenReturn(Optional.of(target));
        when(stages.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Stage updated = service.update(5L, "In development", null, null, "admin");

        assertThat(updated.getName()).isEqualTo("In development");
        verify(mappings).renameGaugeStage("In dev", "In development");
        verify(issues).renameLifecycleStage("In dev", "In development");
        assertThat(target.getStage()).isEqualTo("In development");
        verify(slaTargets).save(target);
    }

    @Test
    void renameToExistingOtherStageIsRejected() {
        when(stages.findById(5L)).thenReturn(Optional.of(stage(5, "In dev")));
        when(stages.findByNameIgnoreCase("Hold")).thenReturn(Optional.of(stage(9, "Hold")));

        assertThatThrownBy(() -> service.update(5L, "Hold", null, null, "admin"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(mappings, never()).renameGaugeStage(any(), any());
        verify(issues, never()).renameLifecycleStage(any(), any());
    }

    @Test
    void categoryOnlyUpdateDoesNotCascade() {
        when(stages.findById(5L)).thenReturn(Optional.of(stage(5, "In dev")));
        when(stages.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(5L, null, "blocked", null, "admin");

        verify(mappings, never()).renameGaugeStage(any(), any());
        verify(issues, never()).renameLifecycleStage(any(), any());
    }

    // ── delete guard ──────────────────────────────────────────────────────────

    @Test
    void deleteRefusedWhileStageIsReferenced() {
        when(stages.findById(5L)).thenReturn(Optional.of(stage(5, "In dev")));
        when(mappings.countGroupedByGaugeStage()).thenReturn(List.<Object[]>of(new Object[]{"In dev", 2L}));
        when(issues.countGroupedByLifecycleStage()).thenReturn(List.<Object[]>of(new Object[]{"In dev", 7L}));

        assertThatThrownBy(() -> service.delete(5L))
            .isInstanceOf(StageCatalogService.StageInUseException.class)
            .hasMessageContaining("2 mapping(s)")
            .hasMessageContaining("7 issue(s)");
        verify(stages, never()).delete(any(Stage.class));
        verify(slaTargets, never()).deleteByStage(any());
    }

    @Test
    void deleteOfUnreferencedStageRemovesSlaTargetToo() {
        Stage s = stage(5, "Pilot");
        when(stages.findById(5L)).thenReturn(Optional.of(s));
        when(mappings.countGroupedByGaugeStage()).thenReturn(List.of());
        when(issues.countGroupedByLifecycleStage()).thenReturn(List.of());

        service.delete(5L);

        verify(slaTargets).deleteByStage("Pilot");
        verify(stages).delete(s);
    }

    // ── ensureExists ──────────────────────────────────────────────────────────

    @Test
    void ensureExistsInsertsUnknownStageOnly() {
        when(stages.findByNameIgnoreCase("Novel")).thenReturn(Optional.empty());
        when(stages.maxDisplayOrder()).thenReturn(100);
        when(stages.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ensureExists("Novel");
        verify(stages).save(any(Stage.class));

        when(stages.findByNameIgnoreCase("In dev")).thenReturn(Optional.of(stage(1, "In dev")));
        service.ensureExists("In dev");
        verifyNoMoreInteractions(ignoreStubs(stages));
    }
}
