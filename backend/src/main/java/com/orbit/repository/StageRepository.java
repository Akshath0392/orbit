package com.orbit.repository;

import com.orbit.domain.config.Stage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface StageRepository extends JpaRepository<Stage, Long> {
    List<Stage> findAllByOrderByDisplayOrderAscNameAsc();
    Optional<Stage> findByNameIgnoreCase(String name);

    @Query("SELECT COALESCE(MAX(s.displayOrder), 0) FROM Stage s")
    int maxDisplayOrder();
}
