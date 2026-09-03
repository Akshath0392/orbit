package com.orbit.repository;

import com.orbit.domain.issue.JiraIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface JiraIssueRepository extends JpaRepository<JiraIssue, Long> {
    Optional<JiraIssue> findByIssueKey(String issueKey);

    Page<JiraIssue> findByIssueTypeAndClientIdOrderByUpdatedAtDesc(String type, Long clientId, Pageable p);
    Page<JiraIssue> findByIssueTypeOrderByUpdatedAtDesc(String type, Pageable p);

    @Query("SELECT j FROM JiraIssue j WHERE j.issueType='CR' " +
           "AND (:clientId IS NULL OR j.client.id=:clientId) " +
           "AND (:stage IS NULL OR j.lifecycleStage=:stage) " +
           "AND (:search IS NULL OR LOWER(j.issueKey) LIKE :search OR LOWER(j.summary) LIKE :search)")
    Page<JiraIssue> findCrs(@Param("clientId") Long clientId, @Param("stage") String stage,
                             @Param("search") String search, Pageable p);

    // CR board with the mock's full filter set: POD via the
    // project→portfolio join, SM/PjM from the mapped Jira fields, type from
    // the project ops model ('launch+bau' matches both types).
    @Query("SELECT j FROM JiraIssue j WHERE j.issueType='CR' " +
           "AND (:clientId IS NULL OR j.client.id=:clientId) " +
           "AND (:stage IS NULL OR j.lifecycleStage=:stage) " +
           "AND (:portfolioId IS NULL OR j.project.portfolio.id=:portfolioId) " +
           "AND (:sm IS NULL OR j.smOwner=:sm) " +
           "AND (:pjm IS NULL OR j.pjmOwner=:pjm) " +
           "AND (:type IS NULL OR LOWER(COALESCE(j.project.opsModel, 'launch+bau')) LIKE :type) " +
           "AND (:search IS NULL OR LOWER(j.issueKey) LIKE :search OR LOWER(j.summary) LIKE :search)")
    Page<JiraIssue> findCrsFiltered(@Param("clientId") Long clientId, @Param("stage") String stage,
                                     @Param("portfolioId") Long portfolioId, @Param("sm") String sm,
                                     @Param("pjm") String pjm, @Param("type") String type,
                                     @Param("search") String search, Pageable p);

    // Workbench "This Week" / "Attention" counts
    @Query("SELECT j.issueType, COUNT(j) FROM JiraIssue j WHERE j.project.id=:projectId " +
           "AND j.resolvedAt >= :since GROUP BY j.issueType")
    List<Object[]> countResolvedSinceByType(@Param("projectId") Long projectId,
                                             @Param("since") java.time.LocalDateTime since);

    @Query("SELECT COUNT(j) FROM JiraIssue j WHERE j.project.id=:projectId AND j.issueType='CR' " +
           "AND j.resolvedAt IS NULL AND LOWER(j.lifecycleStage) LIKE :stageLike")
    long countOpenCrsByStageLike(@Param("projectId") Long projectId, @Param("stageLike") String stageLike);

    @Query("SELECT DISTINCT j.smOwner FROM JiraIssue j WHERE j.issueType='CR' AND j.smOwner IS NOT NULL ORDER BY j.smOwner")
    List<String> findDistinctCrSmOwners();

    @Query("SELECT DISTINCT j.pjmOwner FROM JiraIssue j WHERE j.issueType='CR' AND j.pjmOwner IS NOT NULL ORDER BY j.pjmOwner")
    List<String> findDistinctCrPjmOwners();

    @Query("SELECT DISTINCT j.jiraStatus, j.issueType FROM JiraIssue j WHERE j.jiraStatus IS NOT NULL")
    List<Object[]> findDistinctJiraStatusAndIssueType();

    @Modifying
    @Query("UPDATE JiraIssue j SET j.lifecycleStage = :stage WHERE j.jiraStatus = :jiraStatus AND j.issueType = :issueType")
    int backfillLifecycleStage(@Param("jiraStatus") String jiraStatus, @Param("issueType") String issueType, @Param("stage") String stage);

    @Query("SELECT j.lifecycleStage, COUNT(j) FROM JiraIssue j " +
           "WHERE j.lifecycleStage IS NOT NULL GROUP BY j.lifecycleStage")
    List<Object[]> countGroupedByLifecycleStage();

    @Modifying
    @Query("UPDATE JiraIssue j SET j.lifecycleStage = :to WHERE j.lifecycleStage = :from")
    int renameLifecycleStage(@Param("from") String from, @Param("to") String to);

    @Query("SELECT j.lifecycleStage, COUNT(j) FROM JiraIssue j WHERE j.issueType='CR' " +
           "AND (:clientId IS NULL OR j.client.id=:clientId) GROUP BY j.lifecycleStage")
    List<Object[]> countCrsByStage(@Param("clientId") Long clientId);

    @Query("SELECT j FROM JiraIssue j WHERE j.issueType='CR' " +
           "AND j.project.id IN :projectIds " +
           "AND (:stage IS NULL OR j.lifecycleStage=:stage) " +
           "AND (:search IS NULL OR LOWER(j.issueKey) LIKE :search OR LOWER(j.summary) LIKE :search)")
    Page<JiraIssue> findCrsByProjectIds(@Param("projectIds") List<Long> projectIds,
                                         @Param("stage") String stage,
                                         @Param("search") String search, Pageable p);

    @Query("SELECT j.lifecycleStage, COUNT(j) FROM JiraIssue j WHERE j.issueType='CR' " +
           "AND j.project.id IN :projectIds GROUP BY j.lifecycleStage")
    List<Object[]> countCrsByStageForProjects(@Param("projectIds") List<Long> projectIds);

    @Query("SELECT j FROM JiraIssue j WHERE j.issueType='PROD_BUG' " +
           "AND (:clientId IS NULL OR j.client.id=:clientId) " +
           "AND (:severity IS NULL OR j.severity=:severity) " +
           "AND (:slaStatus IS NULL OR j.slaStatus=:slaStatus) " +
           "ORDER BY j.createdAt DESC")
    Page<JiraIssue> findProdBugs(@Param("clientId") Long clientId, @Param("severity") String severity,
                                  @Param("slaStatus") String slaStatus, Pageable p);

    @Query("SELECT j FROM JiraIssue j WHERE j.issueType='UAT_BUG' " +
           "AND (:clientId IS NULL OR j.client.id=:clientId) " +
           "AND (:stage IS NULL OR j.lifecycleStage=:stage) " +
           "ORDER BY j.createdAt DESC")
    Page<JiraIssue> findUatBugs(@Param("clientId") Long clientId, @Param("stage") String stage, Pageable p);

    long countByClientIdAndIssueTypeAndLifecycleStageNot(Long clientId, String type, String stage);
    long countByClientIdAndIssueTypeAndSeverityIn(Long clientId, String type, List<String> severities);

    // Null-safe (clientId=null counts across all clients). Excludes closed.
    @Query("SELECT COUNT(j) FROM JiraIssue j WHERE j.issueType=:type " +
           "AND (:clientId IS NULL OR j.client.id=:clientId) " +
           "AND j.severity IN :severities " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN ('Closed','Invalid','Resolved','Canceled'))")
    long countOpenByClientTypeAndSeverityIn(@Param("clientId") Long clientId,
                                             @Param("type") String type,
                                             @Param("severities") List<String> severities);

    @Query("SELECT COUNT(j) FROM JiraIssue j WHERE j.issueType=:type " +
           "AND (:clientId IS NULL OR j.client.id=:clientId) " +
           "AND j.reopenCount > 0 " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN ('Closed','Invalid','Resolved','Canceled'))")
    long countOpenReopenedByClientAndType(@Param("clientId") Long clientId, @Param("type") String type);

    @Query("SELECT COUNT(j) FROM JiraIssue j WHERE j.issueType=:type " +
           "AND (:clientId IS NULL OR j.client.id=:clientId) " +
           "AND (j.assigneeName IS NULL OR j.assigneeName='') " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN ('Closed','Invalid','Resolved','Canceled'))")
    long countOpenUnassignedByClientAndType(@Param("clientId") Long clientId, @Param("type") String type);

    @Query("SELECT COUNT(j) FROM JiraIssue j WHERE j.issueType=:type " +
           "AND (:clientId IS NULL OR j.client.id=:clientId) " +
           "AND j.slaStatus=:slaStatus " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN ('Closed','Invalid','Resolved','Canceled'))")
    long countOpenBySlaStatusAndType(@Param("clientId") Long clientId,
                                      @Param("type") String type,
                                      @Param("slaStatus") String slaStatus);
    long countByClientIdAndIssueType(Long clientId, String type);

    long countByProjectIdAndIssueType(Long projectId, String type);

    // Per-project stats for risk scoring
    long countByProjectIdAndIssueTypeAndLifecycleStage(Long projectId, String type, String stage);
    long countByProjectIdAndIssueTypeAndSlaStatus(Long projectId, String type, String slaStatus);

    @Query("SELECT COUNT(j) FROM JiraIssue j WHERE j.issueType='PROD_BUG' " +
           "AND (:clientId IS NULL OR j.client.id=:clientId) AND j.slaStatus=:slaStatus")
    long countProdBugsBySlaStatus(@Param("clientId") Long clientId, @Param("slaStatus") String slaStatus);

    @Query("SELECT j FROM JiraIssue j WHERE j.project.id=:projectId AND j.issueType='CR' " +
           "AND j.lifecycleStage='Hold' ORDER BY j.updatedAt ASC")
    List<JiraIssue> findHoldingCrsByProject(@Param("projectId") Long projectId);

    @Query("SELECT j FROM JiraIssue j WHERE j.project.id=:projectId AND j.issueType='CR' " +
           "AND j.lifecycleStage NOT IN ('Released','Closed') ORDER BY j.updatedAt DESC")
    List<JiraIssue> findActiveCrsByProject(@Param("projectId") Long projectId);

    @Query("SELECT j FROM JiraIssue j WHERE j.project.id=:projectId " +
           "AND j.lifecycleStage NOT IN ('Released','Closed') " +
           "AND j.updatedAt < :cutoff ORDER BY j.updatedAt ASC")
    List<JiraIssue> findOverdueByProjectId(
        @Param("projectId") Long projectId,
        @Param("cutoff") java.time.LocalDateTime cutoff);

    @Query("SELECT j FROM JiraIssue j WHERE (:projectId IS NULL OR j.project.id=:projectId) " +
           "AND j.updatedAt >= :since ORDER BY j.updatedAt DESC")
    org.springframework.data.domain.Page<JiraIssue> findByProjectIdAndUpdatedAtAfter(
        @Param("projectId") Long projectId,
        @Param("since") java.time.LocalDateTime since,
        org.springframework.data.domain.Pageable pageable);

    @Query("SELECT j FROM JiraIssue j WHERE LOWER(j.summary) LIKE LOWER(:keyword) ORDER BY j.updatedAt DESC")
    org.springframework.data.domain.Page<JiraIssue> searchBySummaryKeyword(
        @Param("keyword") String keyword,
        org.springframework.data.domain.Pageable pageable);

    // For account-detail tracker rows + worklists
    @Query("SELECT j FROM JiraIssue j WHERE j.project.id=:projectId AND j.issueType=:type " +
           "ORDER BY j.updatedAt DESC")
    List<JiraIssue> findByProjectIdAndIssueTypeOrderByUpdatedAtDesc(
        @Param("projectId") Long projectId, @Param("type") String type);

    // Counts only issues whose lifecycleStage is NOT in the excluded list (null = open)
    @Query("SELECT COUNT(j) FROM JiraIssue j WHERE j.project.id=:projectId " +
           "AND j.issueType=:type " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN :excludedStages)")
    long countOpenByProjectAndType(@Param("projectId") Long projectId,
                                   @Param("type") String type,
                                   @Param("excludedStages") List<String> excludedStages);

    @Query("SELECT j.severity, COUNT(j) FROM JiraIssue j WHERE j.issueType='PROD_BUG' " +
           "AND j.project.id IN :projectIds " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN ('Closed','Invalid','Resolved','Canceled')) " +
           "GROUP BY j.severity")
    List<Object[]> countOpenProdBugsBySeverityForProjects(@Param("projectIds") List<Long> projectIds);

    // UAT severity now follows the same P0–P3 convention per product decision.
    @Query("SELECT j.severity, COUNT(j) FROM JiraIssue j WHERE j.issueType='UAT_BUG' " +
           "AND j.project.id IN :projectIds " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN ('Closed','Invalid','Resolved','Canceled')) " +
           "GROUP BY j.severity")
    List<Object[]> countOpenUatBugsBySeverityForProjects(@Param("projectIds") List<Long> projectIds);

    // CR aging buckets (in days) — used by Radar governance Aging tab.
    @Query(value =
        "SELECT CASE " +
        " WHEN EXTRACT(DAY FROM (NOW() - created_at)) <= 3  THEN '0_3' " +
        " WHEN EXTRACT(DAY FROM (NOW() - created_at)) <= 7  THEN '4_7' " +
        " WHEN EXTRACT(DAY FROM (NOW() - created_at)) <= 14 THEN '8_14' " +
        " ELSE '15p' END AS bucket, COUNT(*) " +
        "FROM jira_issues " +
        "WHERE issue_type = 'CR' " +
        "  AND (lifecycle_stage IS NULL OR lifecycle_stage NOT IN ('Closed','Invalid','Released','Canceled')) " +
        "  AND (:projectIds IS NULL OR project_id IN (:projectIds)) " +
        "GROUP BY bucket", nativeQuery = true)
    List<Object[]> countOpenCrsByAgingBucket(@Param("projectIds") List<Long> projectIds);

    @Query("SELECT COUNT(j) FROM JiraIssue j WHERE j.issueType='PROD_BUG' " +
           "AND j.project.id IN :projectIds AND j.slaStatus=:slaStatus " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN ('Closed','Invalid','Resolved','Canceled'))")
    long countOpenBugsBySlaStatusForProjects(@Param("projectIds") List<Long> projectIds,
                                              @Param("slaStatus") String slaStatus);

    @Query("SELECT COUNT(j) FROM JiraIssue j WHERE j.project.id=:projectId " +
           "AND j.issueType=:type AND j.severity=:severity " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN :excludedStages)")
    long countOpenByProjectTypeAndSeverity(@Param("projectId") Long projectId,
                                            @Param("type") String type,
                                            @Param("severity") String severity,
                                            @Param("excludedStages") List<String> excludedStages);

    // Keep old methods for existing callers (deprecated in favour of countOpen*)
    @Query("SELECT j.severity, COUNT(j) FROM JiraIssue j WHERE j.issueType='PROD_BUG' " +
           "AND j.project.id IN :projectIds GROUP BY j.severity")
    List<Object[]> countProdBugsBySeverityForProjects(@Param("projectIds") List<Long> projectIds);

    @Query("SELECT COUNT(j) FROM JiraIssue j WHERE j.issueType='PROD_BUG' " +
           "AND j.project.id IN :projectIds AND j.slaStatus=:slaStatus")
    long countBugsBySlaStatusForProjects(@Param("projectIds") List<Long> projectIds,
                                          @Param("slaStatus") String slaStatus);

    // ── AM dashboard (docs/plan/orbitter-am-dashboard-plan.md) ──────────────
    // Scalar rows for the stage/owner matrices — grouped in the service layer
    // because %-within-SLA needs per-row age vs a configurable per-stage target.
    @Query("SELECT c.name, j.lifecycleStage, j.assigneeName, p.opsModel, j.createdAt, j.smOwner, j.pjmOwner " +
           "FROM JiraIssue j JOIN j.client c JOIN j.project p " +
           "WHERE j.issueType='CR' " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN ('Released','Closed','Invalid','Resolved','Canceled')) " +
           "AND (:portfolioId IS NULL OR p.portfolio.id=:portfolioId)")
    List<Object[]> findOpenAmCrRows(@Param("portfolioId") Long portfolioId);

    // SLA-breach escalation sweep — full open-CR entities (need the
    // issue key for the dedup ledger + owner fields); client + project fetched so
    // the sweep can read the project (for Slack channel resolution) outside a tx.
    @Query("SELECT j FROM JiraIssue j LEFT JOIN FETCH j.client LEFT JOIN FETCH j.project WHERE j.issueType='CR' " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN ('Released','Closed','Invalid','Resolved','Canceled'))")
    List<JiraIssue> findOpenCrsForEscalation();

    @Query("SELECT j.createdAt, j.resolvedAt, j.severity FROM JiraIssue j " +
           "WHERE j.issueType='PROD_BUG' " +
           "AND (:portfolioId IS NULL OR j.project.portfolio.id=:portfolioId) " +
           "AND (j.createdAt >= :since OR j.resolvedAt IS NULL)")
    List<Object[]> findAmProdBugRows(@Param("portfolioId") Long portfolioId,
                                      @Param("since") java.time.LocalDateTime since);

    @Query("SELECT c.name, j.severity, COUNT(j) FROM JiraIssue j JOIN j.client c " +
           "WHERE j.issueType='PROD_BUG' AND j.resolvedAt IS NULL " +
           "AND (:portfolioId IS NULL OR j.project.portfolio.id=:portfolioId) " +
           "GROUP BY c.name, j.severity")
    List<Object[]> countOpenProdBugsByClientAndSeverity(@Param("portfolioId") Long portfolioId);

    @Query("SELECT j FROM JiraIssue j WHERE j.issueType='CR' " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN ('Released','Closed','Invalid','Resolved','Canceled')) " +
           "AND (:portfolioId IS NULL OR j.project.portfolio.id=:portfolioId) " +
           "AND (:clientId IS NULL OR j.client.id=:clientId) " +
           "AND (:clientName IS NULL OR TRIM(j.client.name)=:clientName) " +
           "AND (:stage IS NULL OR j.lifecycleStage=:stage) " +
           "AND (:owner IS NULL OR j.assigneeName=:owner) " +
           "AND (:smOwner IS NULL OR j.smOwner=:smOwner) " +
           "AND (:pjmOwner IS NULL OR j.pjmOwner=:pjmOwner) " +
           "AND (:opsModel IS NULL OR j.project.opsModel LIKE :opsModel) " +
           "ORDER BY j.createdAt ASC")
    Page<JiraIssue> findAmCrDrill(@Param("portfolioId") Long portfolioId,
                                   @Param("clientId") Long clientId,
                                   @Param("clientName") String clientName,
                                   @Param("stage") String stage,
                                   @Param("owner") String owner,
                                   @Param("smOwner") String smOwner,
                                   @Param("pjmOwner") String pjmOwner,
                                   @Param("opsModel") String opsModel, Pageable p);

    // ── AM V3 reforms (docs/plan/orbitter-am-v3-reforms-plan.md) ────────────
    // POD benchmarking scores over every portfolio in one query (projects
    // without a portfolio are outside the POD ranking by definition).
    @Query("SELECT pf.id, pf.name, j.lifecycleStage, j.createdAt, p.opsModel " +
           "FROM JiraIssue j JOIN j.project p JOIN p.portfolio pf " +
           "WHERE j.issueType='CR' " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN ('Released','Closed','Invalid','Resolved','Canceled'))")
    List<Object[]> findOpenCrRowsAllPortfolios();

    @Query("SELECT pf.id, j.severity, COUNT(j) FROM JiraIssue j JOIN j.project p JOIN p.portfolio pf " +
           "WHERE j.issueType='PROD_BUG' AND j.resolvedAt IS NULL GROUP BY pf.id, j.severity")
    List<Object[]> countOpenProdBugsByPortfolioAndSeverity();

    // Client master page — open CRs (stage/age/type) and resolved CRs (lead
    // time / throughput trend) for one client.
    @Query("SELECT j.lifecycleStage, j.createdAt, p.opsModel, j.assigneeName " +
           "FROM JiraIssue j JOIN j.project p " +
           "WHERE j.issueType='CR' AND j.client.id=:clientId " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN ('Released','Closed','Invalid','Resolved','Canceled'))")
    List<Object[]> findOpenCrRowsForClient(@Param("clientId") Long clientId);

    @Query("SELECT j.createdAt, j.resolvedAt, p.opsModel, j.reopenCount, j.firstInProgressAt FROM JiraIssue j JOIN j.project p " +
           "WHERE j.issueType='CR' AND j.client.id=:clientId AND j.resolvedAt >= :since")
    List<Object[]> findResolvedCrRowsForClient(@Param("clientId") Long clientId,
                                               @Param("since") java.time.LocalDateTime since);

    @Query("SELECT j.createdAt, j.resolvedAt, j.severity FROM JiraIssue j " +
           "WHERE j.issueType='PROD_BUG' AND j.client.id=:clientId " +
           "AND (j.createdAt >= :since OR j.resolvedAt IS NULL)")
    List<Object[]> findProdBugRowsForClient(@Param("clientId") Long clientId,
                                            @Param("since") java.time.LocalDateTime since);

    // ── AM widget parity (docs/plan/orbitter-am-widget-parity-plan.md) ──────
    // W11 CSAT drill — every issue of the POD's clients grouped by work type
    // (issue type × ops model) and lifecycle stage; the controller buckets
    // stages into Backlog / In Progress / Closed.
    @Query("SELECT c.name, c.id, j.issueType, p.opsModel, j.lifecycleStage, COUNT(j) " +
           "FROM JiraIssue j JOIN j.client c JOIN j.project p " +
           "WHERE (:portfolioId IS NULL OR p.portfolio.id=:portfolioId) " +
           "GROUP BY c.name, c.id, j.issueType, p.opsModel, j.lifecycleStage")
    List<Object[]> findCsatDrillRows(@Param("portfolioId") Long portfolioId);

    // Changelog backfill work queue (F3) — cursor = changelog_synced_at
    long countByChangelogSyncedAtIsNull();

    @Query("SELECT j FROM JiraIssue j WHERE j.changelogSyncedAt IS NULL ORDER BY j.updatedAt DESC")
    List<JiraIssue> findByChangelogSyncedAtIsNull(Pageable page);

    @Query("SELECT j FROM JiraIssue j WHERE j.project.id=:projectId AND j.changelogSyncedAt IS NULL ORDER BY j.updatedAt DESC")
    List<JiraIssue> findByProjectIdAndChangelogSyncedAtIsNull(@Param("projectId") Long projectId, Pageable page);

    // W18 sprint scope — the account's CRs, open plus recently delivered;
    // the controller derives the delivery phase from the lifecycle stage.
    @Query("SELECT j.issueKey, j.summary, j.jiraStatus, j.lifecycleStage, j.createdAt, j.resolvedAt, j.assigneeName, j.currentSprintName " +
           "FROM JiraIssue j WHERE j.issueType='CR' AND j.project.id=:projectId " +
           "AND (j.resolvedAt IS NULL AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN ('Released','Closed','Invalid','Resolved','Canceled')) " +
           "     OR j.resolvedAt >= :deliveredSince) " +
           "ORDER BY j.createdAt ASC")
    List<Object[]> findSprintScopeRowsForProject(@Param("projectId") Long projectId,
                                                 @Param("deliveredSince") java.time.LocalDateTime deliveredSince);

    // ── Bulk grouped counts (dashboard perf) — one query for N projects/clients ──

    @Query("SELECT j.project.id, COUNT(j) FROM JiraIssue j WHERE j.project.id IN :projectIds " +
           "AND j.issueType=:type GROUP BY j.project.id")
    List<Object[]> countByProjectsAndTypeGrouped(@Param("projectIds") List<Long> projectIds,
                                                 @Param("type") String type);

    @Query("SELECT j.project.id, COUNT(j) FROM JiraIssue j WHERE j.project.id IN :projectIds " +
           "AND j.issueType=:type " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN :excludedStages) " +
           "GROUP BY j.project.id")
    List<Object[]> countOpenByProjectsAndTypeGrouped(@Param("projectIds") List<Long> projectIds,
                                                     @Param("type") String type,
                                                     @Param("excludedStages") List<String> excludedStages);

    // Non-open-filtered, matches countByProjectIdAndIssueTypeAndSlaStatus.
    @Query("SELECT j.project.id, COUNT(j) FROM JiraIssue j WHERE j.project.id IN :projectIds " +
           "AND j.issueType=:type AND j.slaStatus=:slaStatus GROUP BY j.project.id")
    List<Object[]> countByProjectsTypeAndSlaStatusGrouped(@Param("projectIds") List<Long> projectIds,
                                                          @Param("type") String type,
                                                          @Param("slaStatus") String slaStatus);

    @Query("SELECT j.project.id, j.severity, COUNT(j) FROM JiraIssue j WHERE j.project.id IN :projectIds " +
           "AND j.issueType=:type " +
           "AND (j.lifecycleStage IS NULL OR j.lifecycleStage NOT IN :excludedStages) " +
           "GROUP BY j.project.id, j.severity")
    List<Object[]> countOpenByProjectsTypeAndSeverityGrouped(@Param("projectIds") List<Long> projectIds,
                                                             @Param("type") String type,
                                                             @Param("excludedStages") List<String> excludedStages);

    @Query("SELECT j.project.id, COUNT(j) FROM JiraIssue j WHERE j.project.id IN :projectIds " +
           "AND j.issueType=:type AND j.lifecycleStage IN :stages GROUP BY j.project.id")
    List<Object[]> countByProjectsTypeAndLifecycleStagesGrouped(@Param("projectIds") List<Long> projectIds,
                                                                @Param("type") String type,
                                                                @Param("stages") List<String> stages);

    @Query("SELECT j.client.id, COUNT(j) FROM JiraIssue j WHERE j.client.id IN :clientIds " +
           "AND j.issueType=:type GROUP BY j.client.id")
    List<Object[]> countByClientsAndTypeGrouped(@Param("clientIds") List<Long> clientIds,
                                                @Param("type") String type);

    @Query("SELECT j.client.id, COUNT(j) FROM JiraIssue j WHERE j.client.id IN :clientIds " +
           "AND j.issueType=:type AND j.severity IN :severities GROUP BY j.client.id")
    List<Object[]> countByClientsTypeAndSeverityInGrouped(@Param("clientIds") List<Long> clientIds,
                                                          @Param("type") String type,
                                                          @Param("severities") List<String> severities);

    // Same "not yet in stage" semantics as the per-client
    // countByClientIdAndIssueTypeAndLifecycleStageNot it replaces in bulk paths.
    @Query("SELECT j.client.id, COUNT(j) FROM JiraIssue j WHERE j.client.id IN :clientIds " +
           "AND j.issueType=:type AND j.lifecycleStage <> :stage GROUP BY j.client.id")
    List<Object[]> countByClientsTypeAndLifecycleStageNotGrouped(@Param("clientIds") List<Long> clientIds,
                                                                 @Param("type") String type,
                                                                 @Param("stage") String stage);

    @Query("SELECT j FROM JiraIssue j WHERE j.project.id IN :projectIds AND j.issueType='CR' " +
           "AND j.lifecycleStage='Hold' ORDER BY j.updatedAt ASC")
    List<JiraIssue> findHoldingCrsByProjects(@Param("projectIds") List<Long> projectIds);
}
