package com.orbit.repository;

import com.orbit.domain.agent.AgentMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AgentMemoryRepository extends JpaRepository<AgentMemory, Long> {

    @Query("SELECT m FROM AgentMemory m WHERE m.agentId = :agentId " +
           "AND (:projectId IS NULL OR m.projectId = :projectId) " +
           "AND m.memKey = :memKey ORDER BY m.createdAt DESC")
    Optional<AgentMemory> findByAgentIdAndProjectIdAndMemKey(
        @Param("agentId") Long agentId,
        @Param("projectId") Long projectId,
        @Param("memKey") String memKey);

    @Query("SELECT m FROM AgentMemory m WHERE m.agentId = :agentId " +
           "AND (:projectId IS NULL OR m.projectId = :projectId) " +
           "ORDER BY m.createdAt DESC")
    List<AgentMemory> findByAgentIdAndProjectId(
        @Param("agentId") Long agentId,
        @Param("projectId") Long projectId);

    @Query("SELECT m FROM AgentMemory m WHERE m.memoryType = :memoryType " +
           "AND m.memKey = :memKey ORDER BY m.createdAt DESC")
    List<AgentMemory> findByMemoryTypeAndMemKey(
        @Param("memoryType") String memoryType,
        @Param("memKey") String memKey);
}
