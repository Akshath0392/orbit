package com.orbit.repository;

import com.orbit.domain.account.ProjectRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface ProjectReleaseRepository extends JpaRepository<ProjectRelease, Long> {
    List<ProjectRelease> findByProjectIdAndReleaseDateBetweenOrderByReleaseDateAsc(
        Long projectId, LocalDate from, LocalDate to);
    List<ProjectRelease> findByProjectIdOrderByReleaseDateAsc(Long projectId);
    List<ProjectRelease> findByProjectIdInAndReleaseDateBetweenOrderByReleaseDateAsc(
        java.util.Collection<Long> projectIds, LocalDate from, LocalDate to);
}
