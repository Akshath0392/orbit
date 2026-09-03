package com.orbit.repository;

import com.orbit.domain.config.HealthProfileWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HealthProfileWeightRepository extends JpaRepository<HealthProfileWeight, Long> {
    List<HealthProfileWeight> findByStageOrderByMetricAsc(String stage);
    Optional<HealthProfileWeight> findByStageAndMetric(String stage, String metric);
    List<HealthProfileWeight> findAllByOrderByStageAscMetricAsc();
}
