package com.orbit.repository;

import com.orbit.domain.agent.CrEscalation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CrEscalationRepository extends JpaRepository<CrEscalation, String> {

    /** Issue keys proposed at or after the cutoff — i.e. still within the cooldown window. */
    List<CrEscalation> findByLastProposedAtGreaterThanEqual(LocalDateTime cutoff);
}
