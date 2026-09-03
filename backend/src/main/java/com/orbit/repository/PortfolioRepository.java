package com.orbit.repository;

import com.orbit.domain.client.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByActiveTrue();
    List<Portfolio> findByClientsIdAndActiveTrue(Long clientId);  // traverses portfolio_clients join table
}
