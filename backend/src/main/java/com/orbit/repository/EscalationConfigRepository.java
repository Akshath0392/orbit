package com.orbit.repository;

import com.orbit.domain.alert.EscalationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EscalationConfigRepository extends JpaRepository<EscalationConfig, Long> {
    Optional<EscalationConfig> findByRoleAndPhase(String role, String phase);
    List<EscalationConfig> findByRole(String role);
}
