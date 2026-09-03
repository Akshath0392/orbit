package com.orbit.repository;

import com.orbit.domain.routing.ProdBugQuarantine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProdBugQuarantineRepository extends JpaRepository<ProdBugQuarantine, Long> {

    Optional<ProdBugQuarantine> findByJiraKey(String jiraKey);

    @Query("SELECT q FROM ProdBugQuarantine q WHERE q.resolvedAt IS NULL ORDER BY q.lastSeenAt DESC")
    Page<ProdBugQuarantine> findOpen(Pageable pageable);

    @Query("SELECT q FROM ProdBugQuarantine q WHERE q.resolvedAt IS NULL AND UPPER(q.rawClientCode) = UPPER(:code)")
    List<ProdBugQuarantine> findOpenByCode(@Param("code") String code);

    @Query("SELECT COUNT(q) FROM ProdBugQuarantine q WHERE q.resolvedAt IS NULL")
    long countOpen();
}
