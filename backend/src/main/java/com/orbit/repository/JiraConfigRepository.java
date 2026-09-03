package com.orbit.repository;

import com.orbit.domain.config.JiraConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface JiraConfigRepository extends JpaRepository<JiraConfig, Long> {
    Optional<JiraConfig> findFirstByOrderByIdAsc();
}
