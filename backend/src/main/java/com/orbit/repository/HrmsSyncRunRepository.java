package com.orbit.repository;

import com.orbit.domain.hrms.HrmsSyncRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HrmsSyncRunRepository extends JpaRepository<HrmsSyncRun, Long> {
    List<HrmsSyncRun> findTop20ByOrderByStartedAtDesc();
}
