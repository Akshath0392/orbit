package com.orbit.repository;

import com.orbit.domain.config.SlackConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SlackConfigRepository extends JpaRepository<SlackConfig, Long> {
    Optional<SlackConfig> findFirstByEnabledTrue();
}
