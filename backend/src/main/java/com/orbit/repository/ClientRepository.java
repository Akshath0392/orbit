package com.orbit.repository;

import com.orbit.domain.client.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByCode(String code);
    java.util.List<Client> findByActiveTrue();

    /**
     * Case-insensitive lookup used by the shared prod-bug router. Jira users
     * sometimes type codes in mixed case ("Acme" vs "ACME"); we normalise on
     * the way in and match tolerantly on the way out.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT c FROM Client c WHERE UPPER(TRIM(c.code)) = UPPER(TRIM(:code))")
    Optional<Client> findByCodeIgnoreCase(@org.springframework.data.repository.query.Param("code") String code);

    /**
     * Active-only variant for issue routing — a code left on a retired
     * duplicate row must quarantine, never silently capture prod bugs
     * for a client that no longer renders anywhere.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT c FROM Client c WHERE UPPER(TRIM(c.code)) = UPPER(TRIM(:code)) AND c.active = true")
    Optional<Client> findActiveByCodeIgnoreCase(@org.springframework.data.repository.query.Param("code") String code);
}
