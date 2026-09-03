package com.orbit.repository;

import com.orbit.domain.client.ClientDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClientDependencyRepository extends JpaRepository<ClientDependency, Long> {
    List<ClientDependency> findByClientIdOrderByRaisedAtDesc(Long clientId);
}
