package com.orbit.repository;

import com.orbit.domain.alert.AlertNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertNoteRepository extends JpaRepository<AlertNote, Long> {
    List<AlertNote> findByAlertIdOrderByCreatedAtDesc(Long alertId);
}
