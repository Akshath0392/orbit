package com.orbit.repository;

import com.orbit.domain.uat.UatCycle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UatCycleRepository extends JpaRepository<UatCycle, Long> {
    @Query("SELECT u FROM UatCycle u JOIN u.issue i WHERE " +
           "(:clientId IS NULL OR i.client.id=:clientId) " +
           "ORDER BY u.startedAt DESC")
    Page<UatCycle> findByClientId(@Param("clientId") Long clientId, Pageable p);

    long countByIssueClientIdAndSignOffStatus(Long clientId, String status);

    @Query("SELECT COUNT(u) FROM UatCycle u WHERE u.signOffStatus=:status")
    long countBySignOffStatus(@Param("status") String status);
}
