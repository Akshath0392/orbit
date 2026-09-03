package com.orbit.repository;

import com.orbit.domain.issue.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SprintRepository extends JpaRepository<Sprint, Long> {

    Optional<Sprint> findByJiraSprintId(Long jiraSprintId);

    List<Sprint> findByStateIn(List<String> states);

    // Most recent sprints containing ≥1 issue of the portfolio (active/closed),
    // newest first — tolerates PODs on different boards/cadences.
    @Query("SELECT DISTINCT s FROM Sprint s, SprintIssue si, JiraIssue j " +
           "WHERE si.sprintId = s.id AND si.issueId = j.id " +
           "AND s.state IN ('active','closed') " +
           "AND (:portfolioId IS NULL OR j.project.portfolio.id = :portfolioId) " +
           "ORDER BY s.startDate DESC NULLS LAST")
    List<Sprint> findRecentForPortfolio(@Param("portfolioId") Long portfolioId,
                                        org.springframework.data.domain.Pageable page);

    @Query("SELECT DISTINCT s FROM Sprint s, SprintIssue si, JiraIssue j " +
           "WHERE si.sprintId = s.id AND si.issueId = j.id " +
           "AND s.state IN ('active','closed') " +
           "AND j.client.id = :clientId " +
           "ORDER BY s.startDate DESC NULLS LAST")
    List<Sprint> findRecentForClient(@Param("clientId") Long clientId,
                                     org.springframework.data.domain.Pageable page);
}
