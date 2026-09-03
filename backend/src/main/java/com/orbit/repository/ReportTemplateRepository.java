package com.orbit.repository;

import com.orbit.domain.config.ReportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, Long> {
    Optional<ReportTemplate> findFirstByScopeAndDefaultTemplateTrue(String scope);
    List<ReportTemplate> findByScopeOrderByName(String scope);
}
