package com.orbit.repository;

import com.orbit.domain.config.StageSlaTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StageSlaTargetRepository extends JpaRepository<StageSlaTarget, Long> {
    Optional<StageSlaTarget> findByStage(String stage);
    void deleteByStage(String stage);
}
