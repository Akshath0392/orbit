package com.orbit.repository;

import com.orbit.domain.alert.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {

    List<NotificationEvent> findByProjectIdOrderBySentAtDesc(Long projectId);

    Page<NotificationEvent> findAllByOrderBySentAtDesc(Pageable p);

    boolean existsByPhaseStatusIdAndEventTypeAndSentAtAfter(
        Long phaseStatusId, String eventType, LocalDateTime after);
}
