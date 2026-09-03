package com.orbit.repository;

import com.orbit.domain.agent.AgentDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AgentDefinitionRepository extends JpaRepository<AgentDefinition, Long> {
    List<AgentDefinition> findByEnabledTrue();
    List<AgentDefinition> findBySystemAgentTrue();
    Optional<AgentDefinition> findByName(String name);
}
