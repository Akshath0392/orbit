package com.orbit.repository;

import com.orbit.domain.issue.IssueTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueTransitionRepository extends JpaRepository<IssueTransition, Long> {

    boolean existsByIssueIdAndChangelogIdAndFieldType(Long issueId, String changelogId, String fieldType);

    List<IssueTransition> findByIssueIdAndFieldTypeOrderByTransitionedAtAsc(Long issueId, String fieldType);
}
