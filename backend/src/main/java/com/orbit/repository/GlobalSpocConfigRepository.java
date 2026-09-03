package com.orbit.repository;

import com.orbit.domain.alert.GlobalSpocConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GlobalSpocConfigRepository extends JpaRepository<GlobalSpocConfig, Long> {
    Optional<GlobalSpocConfig> findBySpocType(String spocType);
}
