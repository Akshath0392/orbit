package com.orbit.repository;
import com.orbit.domain.config.RoleScreenConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleScreenConfigRepository extends JpaRepository<RoleScreenConfig, Long> {
    Optional<RoleScreenConfig> findByRoleName(String roleName);
}
