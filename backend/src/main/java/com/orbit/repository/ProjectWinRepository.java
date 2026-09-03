package com.orbit.repository;

import com.orbit.domain.account.ProjectWin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectWinRepository extends JpaRepository<ProjectWin, Long> {
    List<ProjectWin> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
