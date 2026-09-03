package com.orbit.repository;

import com.orbit.domain.client.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findBySlackUserId(String slackUserId);
}
