package com.orbit.repository;

import com.orbit.domain.capacity.Developer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeveloperRepository extends JpaRepository<Developer, Long> {
    List<Developer> findAllByOrderByUtilizationDesc();
    List<Developer> findByTeamOrderByUtilizationDesc(String team);

    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT d.team FROM Developer d WHERE d.team IS NOT NULL AND d.team <> '' ORDER BY d.team")
    List<String> findDistinctTeams();
}
