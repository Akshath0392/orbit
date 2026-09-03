package com.orbit.repository;

import com.orbit.domain.config.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {
    Optional<FeatureFlag> findByFlagKey(String flagKey);
}
