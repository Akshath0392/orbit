package com.orbit.repository;

import com.orbit.domain.agent.AgentDecisionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AgentDecisionLogRepository extends JpaRepository<AgentDecisionLog, Long> {

    @Query("SELECT d FROM AgentDecisionLog d WHERE " +
           "(:agentName IS NULL OR d.agentName=:agentName) " +
           "AND (:outcome IS NULL OR d.outcome=:outcome) " +
           "ORDER BY d.decidedAt DESC")
    Page<AgentDecisionLog> findFiltered(@Param("agentName") String agentName,
                                         @Param("outcome") String outcome,
                                         Pageable p);

    @Query("SELECT COALESCE(SUM(d.tokensUsed),0) FROM AgentDecisionLog d WHERE d.decidedAt >= :since")
    Long sumTokensSince(@Param("since") java.time.LocalDateTime since);
}
