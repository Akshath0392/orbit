package com.orbit.repository;

import com.orbit.domain.report.GeneratedReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface GeneratedReportRepository extends JpaRepository<GeneratedReport, Long> {
    @Query("SELECT r FROM GeneratedReport r WHERE " +
           "(:clientId IS NULL OR r.client.id=:clientId) " +
           "ORDER BY r.generatedAt DESC")
    Page<GeneratedReport> findFiltered(@Param("clientId") Long clientId, Pageable p);

    long countByGeneratedAtAfter(java.time.LocalDateTime since);
}
