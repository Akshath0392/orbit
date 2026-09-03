package com.orbit.repository;

import com.orbit.domain.hrms.HrmsConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface HrmsConfigRepository extends JpaRepository<HrmsConfig, Long> {
    Optional<HrmsConfig> findFirstByOrderByIdAsc();
}
