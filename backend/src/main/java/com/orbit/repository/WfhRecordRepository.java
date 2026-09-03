package com.orbit.repository;

import com.orbit.domain.darwin.WfhRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WfhRecordRepository extends JpaRepository<WfhRecord, Long> {

    Optional<WfhRecord> findByDarwinWfhId(String darwinWfhId);

    List<WfhRecord> findByWfhDateBetweenOrderByWfhDateAsc(LocalDate from, LocalDate to);

    @Query("SELECT w FROM WfhRecord w WHERE w.wfhDate >= :today ORDER BY w.wfhDate ASC")
    List<WfhRecord> findUpcoming(@Param("today") LocalDate today);

    List<WfhRecord> findByStatusInOrderByWfhDateAsc(List<String> statuses);
}
