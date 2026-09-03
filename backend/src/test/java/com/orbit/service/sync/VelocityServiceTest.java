package com.orbit.service.sync;

import com.orbit.domain.issue.JiraIssue;
import com.orbit.domain.issue.Sprint;
import com.orbit.domain.issue.SprintIssue;
import com.orbit.repository.SprintIssueRepository;
import com.orbit.repository.SprintRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * velocitySoS must ignore closed sprints where nothing was
 * pointed (they blanked the POD card), and predictability() must emit the
 * per-sprint series that powers the Predictability trend bars.
 */
class VelocityServiceTest {

    private final SprintRepository sprints = mock(SprintRepository.class);
    private final SprintIssueRepository memberships = mock(SprintIssueRepository.class);
    private final VelocityService service = new VelocityService(sprints, memberships);

    private static Sprint sprint(long id, String name, int startDaysAgo) {
        Sprint s = new Sprint();
        ReflectionTestUtils.setField(s, "id", id);
        s.setJiraSprintId(id);
        s.setName(name);
        s.setState("closed");
        s.setStartDate(LocalDateTime.now().minusDays(startDaysAgo));
        s.setEndDate(LocalDateTime.now().minusDays(startDaysAgo - 14));
        s.setCompleteDate(LocalDateTime.now().minusDays(startDaysAgo - 14));
        return s;
    }

    /** membership row added before the grace cutoff (committed) with the given SP. */
    private static Object[] row(Sprint in, Double sp, boolean resolved) {
        SprintIssue si = new SprintIssue();
        si.setSprintId((Long) ReflectionTestUtils.getField(in, "id"));
        si.setAddedAt(in.getStartDate().minusDays(1));
        JiraIssue j = new JiraIssue();
        j.setStoryPoints(sp == null ? null : BigDecimal.valueOf(sp));
        if (resolved) j.setResolvedAt(in.getStartDate().plusDays(2));
        return new Object[]{si, j};
    }

    private void stub(Sprint s, List<Object[]> rows) {
        when(memberships.findVelocityRows(eq((Long) ReflectionTestUtils.getField(s, "id")), any(), any()))
            .thenReturn(rows);
    }

    @Test
    void velocitySoSSkipsClosedSprintsWithNoPointedWork() {
        Sprint s1 = sprint(1, "S1", 60);   // committed 10, delivered 8 → 80%
        Sprint s2 = sprint(2, "S2", 40);   // committed 5, delivered 5 → 100%
        Sprint s3 = sprint(3, "S3", 20);   // latest, fully unpointed → committed 0
        when(sprints.findRecentForPortfolio(eq(9L), any(Pageable.class))).thenReturn(List.of(s3, s2, s1));
        stub(s1, List.<Object[]>of(row(s1, 8.0, true), row(s1, 2.0, false)));
        stub(s2, List.<Object[]>of(row(s2, 5.0, true)));
        stub(s3, List.<Object[]>of(row(s3, null, false), row(s3, null, true)));

        Map<String, Object> out = service.velocityPayload(9L, null, 6);

        @SuppressWarnings("unchecked")
        Map<String, Object> sos = (Map<String, Object>) out.get("velocitySoS");
        assertNotNull(sos, "SoS must exist — S2 has pointed work even though S3 is newer");
        assertEquals(100.0, sos.get("pct"));
        assertEquals("S2", sos.get("sprint"));
        assertEquals(20.0, sos.get("delta"));   // 100 vs S1's 80
    }

    @Test
    void velocitySoSAbsentWhenNoClosedSprintHasCommittedWork() {
        Sprint s1 = sprint(1, "S1", 20);
        when(sprints.findRecentForPortfolio(eq(9L), any(Pageable.class))).thenReturn(List.of(s1));
        stub(s1, List.<Object[]>of(row(s1, null, true)));

        Map<String, Object> out = service.velocityPayload(9L, null, 6);
        assertNull(out.get("velocitySoS"));
        assertEquals(true, out.get("dataAvailable")); // section still renders the sprint bars
    }

    @Test
    void predictabilityEmitsPerSprintSeriesSkippingUnpointedSprints() {
        Sprint s1 = sprint(1, "S1", 60);
        Sprint s2 = sprint(2, "S2", 40);
        Sprint s3 = sprint(3, "S3", 20);   // unpointed — must not appear in the series
        when(sprints.findRecentForClient(eq(5L), any(Pageable.class))).thenReturn(List.of(s3, s2, s1));
        stub(s1, List.<Object[]>of(row(s1, 8.0, true), row(s1, 2.0, false)));
        stub(s2, List.<Object[]>of(row(s2, 5.0, true)));
        stub(s3, List.<Object[]>of(row(s3, null, false)));

        Map<String, Object> out = service.predictability(5L, 6);

        assertEquals(true, out.get("dataAvailable"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> per = (List<Map<String, Object>>) out.get("perSprint");
        assertEquals(2, per.size());
        assertEquals(List.of("S1", "S2"), per.stream().map(r -> r.get("label")).toList());
        assertEquals(80L, per.get(0).get("commitmentPct"));   // 8 of 10 committed delivered
        assertEquals(20L, per.get(0).get("spilloverPct"));
        assertEquals(100L, per.get(1).get("commitmentPct"));
    }
}
