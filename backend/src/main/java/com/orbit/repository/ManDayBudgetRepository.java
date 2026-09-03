package com.orbit.repository;

import com.orbit.domain.client.ManDayBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ManDayBudgetRepository extends JpaRepository<ManDayBudget, Long> {
    Optional<ManDayBudget> findByProjectId(Long projectId);
    java.util.List<ManDayBudget> findByProjectIdIn(java.util.List<Long> projectIds);
}
