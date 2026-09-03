package com.orbit.repository;

import com.orbit.domain.issue.SprintIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SprintIssueRepository extends JpaRepository<SprintIssue, Long> {

    Optional<SprintIssue> findBySprintIdAndIssueId(Long sprintId, Long issueId);

    List<SprintIssue> findBySprintId(Long sprintId);

    List<SprintIssue> findByIssueId(Long issueId);

    // Velocity source rows: membership + issue facts for one sprint, scoped by
    // portfolio or client (null = all).
    @Query("SELECT si, j FROM SprintIssue si, JiraIssue j " +
           "WHERE si.issueId = j.id AND si.sprintId = :sprintId " +
           "AND (:portfolioId IS NULL OR j.project.portfolio.id = :portfolioId) " +
           "AND (:clientId IS NULL OR j.client.id = :clientId)")
    List<Object[]> findVelocityRows(@Param("sprintId") Long sprintId,
                                    @Param("portfolioId") Long portfolioId,
                                    @Param("clientId") Long clientId);
}
