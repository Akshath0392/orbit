package com.orbit.repository;

import com.orbit.domain.account.GovernanceMeeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface GovernanceMeetingRepository extends JpaRepository<GovernanceMeeting, Long> {
    List<GovernanceMeeting> findByProjectIdOrderByNextDueAsc(Long projectId);
    List<GovernanceMeeting> findByPortfolioIdOrderByNextDueAsc(Long portfolioId);
    List<GovernanceMeeting> findByPortfolioIdAndNextDueBetweenOrderByNextDueAsc(Long portfolioId, LocalDate from, LocalDate to);
    List<GovernanceMeeting> findByProjectIdInOrderByNextDueAsc(java.util.Collection<Long> projectIds);
}
