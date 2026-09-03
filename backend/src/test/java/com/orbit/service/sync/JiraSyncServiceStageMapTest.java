package com.orbit.service.sync;

import com.orbit.domain.client.LifecycleMapping;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same Jira status can map to different gauge stages per issue type;
 * the stage map must resolve type-first, not last-write-wins.
 */
class JiraSyncServiceStageMapTest {

    private static LifecycleMapping m(String issueType, String jiraStatus, String gauge) {
        return LifecycleMapping.builder().issueType(issueType).jiraStatus(jiraStatus).gaugeStage(gauge).build();
    }

    private static final Map<String, String> MAP = JiraSyncService.buildStageMap(List.of(
        m("CR", "To Do", "BRD awaited"),
        m("PROD_BUG", "To Do", "New"),
        m("CR", "In Progress", "In dev"),
        m("PROD_BUG", "In Progress", "In progress"),
        m("CR", "Closed", "Closed")));

    @Test
    void divergentStatusResolvesPerIssueType() {
        assertThat(JiraSyncService.stageFor(MAP, "CR", "To Do")).isEqualTo("BRD awaited");
        assertThat(JiraSyncService.stageFor(MAP, "PROD_BUG", "To Do")).isEqualTo("New");
        assertThat(JiraSyncService.stageFor(MAP, "CR", "In Progress")).isEqualTo("In dev");
        assertThat(JiraSyncService.stageFor(MAP, "PROD_BUG", "In Progress")).isEqualTo("In progress");
    }

    @Test
    void unmappedTypeFallsBackToAnyTypeThenRawStatus() {
        // UAT_BUG has no rows → bare-status fallback (first mapping row wins deterministically).
        assertThat(JiraSyncService.stageFor(MAP, "UAT_BUG", "Closed")).isEqualTo("Closed");
        assertThat(JiraSyncService.stageFor(MAP, "UAT_BUG", "To Do")).isEqualTo("BRD awaited");
        // Completely unmapped status → raw status passthrough (old behaviour preserved).
        assertThat(JiraSyncService.stageFor(MAP, "CR", "Weird Status")).isEqualTo("Weird Status");
    }

    @Test
    void nullIssueTypeStillResolvesViaBareStatus() {
        assertThat(JiraSyncService.stageFor(MAP, null, "In Progress")).isEqualTo("In dev");
    }

    // ALL is the explicit wildcard — it owns the bare-status
    // fallback regardless of row order, but never beats a type's own row.

    @Test
    void allWildcardOwnsFallbackForUnmappedTypes() {
        Map<String, String> map = JiraSyncService.buildStageMap(List.of(
            m("CR", "On Hold", "CR hold"),   // inserted first — would win putIfAbsent
            m("ALL", "On Hold", "Hold")));
        assertThat(JiraSyncService.stageFor(map, "UAT_BUG", "On Hold")).isEqualTo("Hold");
        assertThat(JiraSyncService.stageFor(map, "TASK", "On Hold")).isEqualTo("Hold");
    }

    @Test
    void typeSpecificRowStillBeatsAllWildcardForItsOwnType() {
        Map<String, String> map = JiraSyncService.buildStageMap(List.of(
            m("ALL", "On Hold", "Hold"),
            m("CR", "On Hold", "CR hold")));
        assertThat(JiraSyncService.stageFor(map, "CR", "On Hold")).isEqualTo("CR hold");
        assertThat(JiraSyncService.stageFor(map, "PROD_BUG", "On Hold")).isEqualTo("Hold");
    }

    @Test
    void allOnlyStatusResolvesForEveryType() {
        Map<String, String> map = JiraSyncService.buildStageMap(List.of(
            m("ALL", "Triage", "New")));
        assertThat(JiraSyncService.stageFor(map, "CR", "Triage")).isEqualTo("New");
        assertThat(JiraSyncService.stageFor(map, "UAT_BUG", "Triage")).isEqualTo("New");
        assertThat(JiraSyncService.stageFor(map, null, "Triage")).isEqualTo("New");
    }
}
