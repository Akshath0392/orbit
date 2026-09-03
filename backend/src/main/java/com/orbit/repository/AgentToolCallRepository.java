package com.orbit.repository;

import com.orbit.domain.agent.AgentToolCall;
import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface AgentToolCallRepository extends JpaRepository<AgentToolCall, Long> {

    List<AgentToolCall> findByRunId(Long runId);

    @Query("SELECT tc FROM AgentToolCall tc WHERE tc.hitlOutcome = 'AWAITING_HITL' " +
           "ORDER BY tc.calledAt DESC")
    List<AgentToolCall> findPendingHitl();
}
