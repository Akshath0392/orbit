package com.orbit.repository;

import com.orbit.domain.slack.SlackMagicLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlackMagicLinkRepository extends JpaRepository<SlackMagicLink, Long> {
    Optional<SlackMagicLink> findByToken(String token);
}
