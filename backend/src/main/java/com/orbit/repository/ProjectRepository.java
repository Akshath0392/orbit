package com.orbit.repository;

import com.orbit.domain.client.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByClientIdAndActiveTrue(Long clientId);
    List<Project> findByClientIdInAndActiveTrue(List<Long> clientIds);
    List<Project> findByPortfolioIdAndActiveTrue(Long portfolioId);
    List<Project> findByActiveTrue();

    // The radar card builder touches client + portfolio names for every
    // project — fetch them in the same round-trip.
    @org.springframework.data.jpa.repository.Query(
        "SELECT p FROM Project p LEFT JOIN FETCH p.client LEFT JOIN FETCH p.portfolio WHERE p.active=true")
    List<Project> findActiveWithClientAndPortfolio();

    /** Orbit projects flagged as shared prod-bug pools (V80). Usually 0 or 1 row today. */
    List<Project> findBySharedProdBugsTrue();
}
