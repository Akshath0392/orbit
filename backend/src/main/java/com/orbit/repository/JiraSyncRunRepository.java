package com.orbit.repository;
import com.orbit.domain.config.JiraSyncRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JiraSyncRunRepository extends JpaRepository<JiraSyncRun, Long> {
    List<JiraSyncRun> findTop20ByOrderByStartedAtDesc();
    List<JiraSyncRun> findByStatus(String status);
}
