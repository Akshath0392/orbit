package com.orbit.repository;

import com.orbit.domain.darwin.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    List<LeaveBalance> findByDarwinEmpIdOrderByLeaveTypeAsc(String empId);
    List<LeaveBalance> findByUserIdOrderByLeaveTypeAsc(Long userId);
    Optional<LeaveBalance> findByDarwinEmpIdAndLeaveType(String empId, String leaveType);
}
