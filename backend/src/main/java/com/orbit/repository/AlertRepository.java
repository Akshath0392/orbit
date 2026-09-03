package com.orbit.repository;

import com.orbit.domain.alert.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query("SELECT a FROM Alert a WHERE " +
           "(:severity IS NULL OR a.severity=:severity) " +
           "AND (:status IS NULL OR a.status=:status) " +
           "AND (:clientId IS NULL OR a.client.id=:clientId) " +
           "AND (:type IS NULL OR a.alertType=:type) " +
           "ORDER BY a.createdAt DESC")
    Page<Alert> findFiltered(@Param("severity") String severity,
                              @Param("status") String status,
                              @Param("clientId") Long clientId,
                              @Param("type") String type,
                              Pageable p);

    default Page<Alert> findFiltered(String severity, String status, Long clientId, Pageable p) {
        return findFiltered(severity, status, clientId, null, p);
    }

    @Query("SELECT DISTINCT a.alertType FROM Alert a WHERE a.alertType IS NOT NULL ORDER BY a.alertType")
    List<String> findDistinctAlertTypes();

    long countBySeverityAndStatus(String severity, String status);
    List<Alert> findTop5ByStatusOrderByCreatedAtDesc(String status);

    // Per-project / per-client counts for risk scoring
    long countByClientIdAndSeverityAndStatus(Long clientId, String severity, String status);
    long countByProjectIdAndSeverityAndStatus(Long projectId, String severity, String status);
    long countByProjectIdAndStatus(Long projectId, String status);
    List<Alert> findByClientIdAndStatusOrderByCreatedAtDesc(Long clientId, String status);

    @Query("SELECT a.client.id, a.severity, a.status, COUNT(a) FROM Alert a " +
           "WHERE a.client.id IN :clientIds GROUP BY a.client.id, a.severity, a.status")
    List<Object[]> countBySeverityAndStatusGroupedByClient(@Param("clientIds") List<Long> clientIds);

    @Query("SELECT a.project.id, a.severity, a.status, COUNT(a) FROM Alert a " +
           "WHERE a.project.id IN :projectIds GROUP BY a.project.id, a.severity, a.status")
    List<Object[]> countBySeverityAndStatusGroupedByProject(@Param("projectIds") List<Long> projectIds);
}
