package com.orbit.repository;

import com.orbit.domain.alert.PhaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PhaseStatusRepository extends JpaRepository<PhaseStatus, Long> {

    List<PhaseStatus> findByProjectId(Long projectId);

    Optional<PhaseStatus> findByProjectIdAndPhase(Long projectId, String phase);

    @Query("SELECT ps FROM PhaseStatus ps WHERE ps.endDate IS NOT NULL " +
           "AND ps.status NOT IN ('COMPLETED') ORDER BY ps.endDate ASC")
    List<PhaseStatus> findAllActive();

    @Query("SELECT ps FROM PhaseStatus ps WHERE ps.endDate = :date AND ps.status NOT IN ('COMPLETED')")
    List<PhaseStatus> findByEndDate(@Param("date") LocalDate date);

    @Query("SELECT ps FROM PhaseStatus ps WHERE ps.endDate < :today " +
           "AND ps.status NOT IN ('COMPLETED', 'DELAYED_SYSTEM')")
    List<PhaseStatus> findNewlyOverdue(@Param("today") LocalDate today);
}
