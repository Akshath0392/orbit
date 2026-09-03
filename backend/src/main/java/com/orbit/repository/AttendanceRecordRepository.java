package com.orbit.repository;

import com.orbit.domain.darwin.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findByDarwinEmpIdAndAttendanceDate(String empId, LocalDate date);
    List<AttendanceRecord> findByAttendanceDateBetweenOrderByAttendanceDateAsc(LocalDate from, LocalDate to);
    List<AttendanceRecord> findByUserIdOrderByAttendanceDateDesc(Long userId);

    @Query("SELECT a FROM AttendanceRecord a WHERE a.attendanceDate = :date ORDER BY a.darwinEmpId ASC")
    List<AttendanceRecord> findByDate(@Param("date") LocalDate date);
}
