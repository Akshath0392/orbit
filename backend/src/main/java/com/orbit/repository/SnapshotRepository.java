package com.orbit.repository;

import com.orbit.domain.snapshot.Snapshot;
import com.orbit.domain.snapshot.SnapshotState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {

    @Query("SELECT s FROM Snapshot s WHERE s.dedupKey = :key AND s.state = 'READY' " +
           "AND s.completedAt > :since ORDER BY s.completedAt DESC")
    List<Snapshot> findReadySince(@Param("key") String key, @Param("since") LocalDateTime since);

    default Optional<Snapshot> findFirstReadySince(String key, LocalDateTime since) {
        return findReadySince(key, since).stream().findFirst();
    }

    @Query("SELECT s FROM Snapshot s WHERE s.dedupKey = :key " +
           "AND s.state IN ('PENDING', 'RUNNING') ORDER BY s.createdAt DESC")
    List<Snapshot> findInflight(@Param("key") String key);

    default Optional<Snapshot> findFirstInflight(String key) {
        return findInflight(key).stream().findFirst();
    }

    @Query("SELECT s FROM Snapshot s WHERE s.state IN ('PENDING', 'RUNNING') " +
           "AND s.createdAt < :cutoff")
    List<Snapshot> findStuck(@Param("cutoff") LocalDateTime cutoff);
}
