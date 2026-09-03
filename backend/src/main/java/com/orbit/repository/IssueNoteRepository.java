package com.orbit.repository;

import com.orbit.domain.issue.IssueNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IssueNoteRepository extends JpaRepository<IssueNote, Long> {
    List<IssueNote> findByIssueIdOrderByCreatedAtDesc(Long issueId);
}
