package com.orbit.repository;

import com.orbit.domain.alert.NotificationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, Long> {
    List<NotificationRule> findByEnabledTrue();
    List<NotificationRule> findByTriggerTypeAndEnabledTrue(String triggerType);
    List<NotificationRule> findByPhaseAndEnabledTrue(String phase);
}
