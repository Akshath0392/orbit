package com.orbit.repository;

import com.orbit.domain.report.ReportSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, Long> {

    @Query("SELECT s FROM ReportSchedule s WHERE :clientId IS NULL OR s.client.id=:clientId ORDER BY s.id DESC")
    Page<ReportSchedule> findFiltered(@Param("clientId") Long clientId, Pageable p);

    List<ReportSchedule> findByActiveTrueOrderByIdAsc();
    long countByActive(Boolean active);
}
