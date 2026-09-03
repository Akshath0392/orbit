package com.orbit.repository;

import com.orbit.domain.issue.JiraWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JiraWebhookEventRepository extends JpaRepository<JiraWebhookEvent, Long> {

    /** Used for idempotency: check if this webhook ID was already processed. */
    Optional<JiraWebhookEvent> findByWebhookId(String webhookId);
}
