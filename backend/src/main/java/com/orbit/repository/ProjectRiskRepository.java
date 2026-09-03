package com.orbit.repository;

import com.orbit.domain.account.ProjectRisk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectRiskRepository extends JpaRepository<ProjectRisk, Long> {
    List<ProjectRisk> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
