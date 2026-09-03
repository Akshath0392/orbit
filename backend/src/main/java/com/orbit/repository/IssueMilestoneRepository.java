package com.orbit.repository;

import com.orbit.domain.issue.IssueMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IssueMilestoneRepository extends JpaRepository<IssueMilestone, Long> {
    List<IssueMilestone> findByIssueId(Long issueId);
    long countByIssueClientIdAndIsTbcTrue(Long clientId);

    // Per-project milestone stats
    long countByIssueProjectIdAndIsTbcTrue(Long projectId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT COUNT(m) FROM IssueMilestone m WHERE m.issue.project.id=:projectId " +
        "AND m.isTbc=false AND m.targetDate < CURRENT_DATE AND m.actualDate IS NULL")
    long countOverdueByProjectId(@org.springframework.data.repository.query.Param("projectId") Long projectId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT m.issue.client.id, COUNT(m) FROM IssueMilestone m WHERE m.issue.client.id IN :clientIds " +
        "AND m.isTbc=true GROUP BY m.issue.client.id")
    List<Object[]> countTbcGroupedByClient(@org.springframework.data.repository.query.Param("clientIds") List<Long> clientIds);

    @org.springframework.data.jpa.repository.Query(
        "SELECT m.issue.project.id, COUNT(m) FROM IssueMilestone m WHERE m.issue.project.id IN :projectIds " +
        "AND m.isTbc=true GROUP BY m.issue.project.id")
    List<Object[]> countTbcGroupedByProject(@org.springframework.data.repository.query.Param("projectIds") List<Long> projectIds);

    @org.springframework.data.jpa.repository.Query(
        "SELECT m.issue.project.id, COUNT(m) FROM IssueMilestone m WHERE m.issue.project.id IN :projectIds " +
        "AND m.isTbc=false AND m.targetDate < CURRENT_DATE AND m.actualDate IS NULL " +
        "GROUP BY m.issue.project.id")
    List<Object[]> countOverdueGroupedByProject(@org.springframework.data.repository.query.Param("projectIds") List<Long> projectIds);
}
