package com.orbit.repository;

import com.orbit.domain.client.SlaRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SlaRuleRepository extends JpaRepository<SlaRule, Long> {
    List<SlaRule> findAllByOrderByClientIdAscSeverityAsc();

    // Default rule (client_id IS NULL)
    Optional<SlaRule> findBySeverityAndClientIsNull(String severity);

    // Client-specific override
    Optional<SlaRule> findBySeverityAndClientId(String severity, Long clientId);
}
