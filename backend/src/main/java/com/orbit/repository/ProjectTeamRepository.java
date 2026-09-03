package com.orbit.repository;

import com.orbit.domain.account.ProjectTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProjectTeamRepository extends JpaRepository<ProjectTeam, Long> {
    Optional<ProjectTeam> findByProjectId(Long projectId);
}
