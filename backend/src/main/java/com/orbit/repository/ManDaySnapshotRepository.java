package com.orbit.repository;

import com.orbit.domain.capacity.ManDaySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ManDaySnapshotRepository extends JpaRepository<ManDaySnapshot, Long> {
    List<ManDaySnapshot> findByProjectIdOrderBySnapshotDateDesc(Long projectId);
    List<ManDaySnapshot> findTop14ByProjectIdOrderBySnapshotDateDesc(Long projectId);

    // Load-path optimization (dashboard perf): latest 14 snapshots per project
    // in one round-trip. Explicit column list so the window-function alias
    // never leaks into entity mapping.
    @org.springframework.data.jpa.repository.Query(value =
        "SELECT t.id, t.project_id, t.snapshot_date, t.burned_days, t.remaining_days, " +
        "       t.burn_rate_per_day, t.forecast_exhaustion " +
        "FROM (SELECT s.*, ROW_NUMBER() OVER (PARTITION BY s.project_id ORDER BY s.snapshot_date DESC) rn " +
        "      FROM man_day_snapshots s WHERE s.project_id IN (:projectIds)) t " +
        "WHERE t.rn <= 14 ORDER BY t.project_id, t.snapshot_date DESC",
        nativeQuery = true)
    List<ManDaySnapshot> findTop14PerProject(
        @org.springframework.data.repository.query.Param("projectIds") List<Long> projectIds);
}
