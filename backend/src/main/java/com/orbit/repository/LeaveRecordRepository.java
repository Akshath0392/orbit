package com.orbit.repository;

import com.orbit.domain.darwin.LeaveRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveRecordRepository extends JpaRepository<LeaveRecord, Long> {
    List<LeaveRecord> findByStatusInOrderByStartDateAsc(List<String> statuses);
    List<LeaveRecord> findByStartDateBetweenOrderByStartDateAsc(LocalDate from, LocalDate to);
    Optional<LeaveRecord> findByDarwinLeaveId(String darwinLeaveId);

    @Query("SELECT l FROM LeaveRecord l WHERE l.endDate >= :today ORDER BY l.startDate ASC")
    List<LeaveRecord> findUpcoming(LocalDate today);
}
