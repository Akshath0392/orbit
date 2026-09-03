package com.orbit.repository;

import com.orbit.domain.account.TeamRoleLabel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRoleLabelRepository extends JpaRepository<TeamRoleLabel, String> {
}
