package com.orbit.repository;

import com.orbit.domain.client.LifecycleMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface LifecycleMappingRepository extends JpaRepository<LifecycleMapping, Long> {
    List<LifecycleMapping> findAllByOrderByIssueTypeAscJiraStatusAsc();
    boolean existsByJiraStatusAndIssueType(String jiraStatus, String issueType);
    java.util.Optional<LifecycleMapping> findFirstByJiraStatusAndIssueType(String jiraStatus, String issueType);

    // ALL is the wildcard type: its rows apply to every issue type,
    // so they belong in any per-type stage list too.
    @Query("SELECT m FROM LifecycleMapping m WHERE m.issueType = :issueType OR m.issueType = 'ALL' " +
           "ORDER BY m.displayOrder ASC NULLS LAST, m.gaugeStage ASC")
    List<LifecycleMapping> findOrderedByIssueType(@Param("issueType") String issueType);

    @Query("SELECT m.gaugeStage, COUNT(m) FROM LifecycleMapping m " +
           "WHERE m.gaugeStage IS NOT NULL GROUP BY m.gaugeStage")
    List<Object[]> countGroupedByGaugeStage();

    @Modifying
    @Query("UPDATE LifecycleMapping m SET m.gaugeStage = :to WHERE m.gaugeStage = :from")
    int renameGaugeStage(@Param("from") String from, @Param("to") String to);
}
