package com.orbit.repository;

import com.orbit.domain.agent.AgentRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {

    Page<AgentRun> findByAgentIdOrderByStartedAtDesc(Long agentId, Pageable pageable);

    @Query("SELECT r FROM AgentRun r WHERE " +
           "(:agentId IS NULL OR r.agentId = :agentId) " +
           "AND (:status IS NULL OR r.status = :status) " +
           "ORDER BY r.startedAt DESC")
    Page<AgentRun> findAllFiltered(@Param("agentId") Long agentId,
                                   @Param("status") String status,
                                   Pageable pageable);
}
