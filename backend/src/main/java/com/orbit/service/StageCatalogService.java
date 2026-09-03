package com.orbit.service;

import com.orbit.domain.config.Stage;
import com.orbit.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Stage catalog CRUD. The catalog owns stage names/order/category; the same
 * strings are denormalized on lifecycle_mappings, jira_issues and
 * stage_sla_targets, so rename cascades to all three in one transaction and
 * delete is refused while mappings or issues still reference the stage.
 */
@Service
public class StageCatalogService {

    public static final List<String> CATEGORIES = List.of(
        "backlog", "in-progress", "qa", "uat", "blocked", "ready", "released", "closed");

    private final StageRepository stages;
    private final LifecycleMappingRepository mappings;
    private final JiraIssueRepository issues;
    private final StageSlaTargetRepository slaTargets;

    public StageCatalogService(StageRepository stages, LifecycleMappingRepository mappings,
                               JiraIssueRepository issues, StageSlaTargetRepository slaTargets) {
        this.stages = stages; this.mappings = mappings; this.issues = issues;
        this.slaTargets = slaTargets;
    }

    public static class StageInUseException extends RuntimeException {
        public final long mappingCount, issueCount;
        public StageInUseException(String name, long mappingCount, long issueCount) {
            super("Stage \"" + name + "\" is still referenced by " + mappingCount
                + " mapping(s) and " + issueCount + " issue(s) — remap them first");
            this.mappingCount = mappingCount; this.issueCount = issueCount;
        }
    }

    public List<Map<String, Object>> list() {
        Map<String, Long> mappingCounts = toCountMap(mappings.countGroupedByGaugeStage());
        Map<String, Long> issueCounts   = toCountMap(issues.countGroupedByLifecycleStage());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Stage s : stages.findAllByOrderByDisplayOrderAscNameAsc()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",           s.getId());
            row.put("name",         s.getName());
            row.put("displayOrder", s.getDisplayOrder());
            row.put("category",     s.getCategory());
            row.put("mappingCount", mappingCounts.getOrDefault(s.getName(), 0L));
            row.put("issueCount",   issueCounts.getOrDefault(s.getName(), 0L));
            out.add(row);
        }
        return out;
    }

    @Transactional
    public Stage create(String name, String category, Integer displayOrder, String updatedBy) {
        String trimmed = validateName(name);
        if (stages.findByNameIgnoreCase(trimmed).isPresent())
            throw new IllegalArgumentException("Stage \"" + trimmed + "\" already exists");
        Stage s = new Stage();
        s.setName(trimmed);
        s.setCategory(validCategoryOrDefault(category));
        s.setDisplayOrder(displayOrder != null ? displayOrder : stages.maxDisplayOrder() + 10);
        s.setUpdatedBy(updatedBy);
        return stages.save(s);
    }

    @Transactional
    public Stage update(Long id, String newName, String category, Integer displayOrder, String updatedBy) {
        Stage s = stages.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Stage " + id + " not found"));
        if (newName != null && !newName.trim().equals(s.getName())) {
            String trimmed = validateName(newName);
            stages.findByNameIgnoreCase(trimmed).ifPresent(other -> {
                if (!other.getId().equals(id))
                    throw new IllegalArgumentException("Stage \"" + trimmed + "\" already exists");
            });
            String from = s.getName();
            mappings.renameGaugeStage(from, trimmed);
            issues.renameLifecycleStage(from, trimmed);
            slaTargets.findByStage(from).ifPresent(t -> { t.setStage(trimmed); slaTargets.save(t); });
            s.setName(trimmed);
        }
        if (category != null)     s.setCategory(validCategoryOrDefault(category));
        if (displayOrder != null) s.setDisplayOrder(displayOrder);
        s.setUpdatedBy(updatedBy);
        s.setUpdatedAt(LocalDateTime.now());
        return stages.save(s);
    }

    @Transactional
    public void delete(Long id) {
        Stage s = stages.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Stage " + id + " not found"));
        long mappingCount = toCountMap(mappings.countGroupedByGaugeStage()).getOrDefault(s.getName(), 0L);
        long issueCount   = toCountMap(issues.countGroupedByLifecycleStage()).getOrDefault(s.getName(), 0L);
        if (mappingCount > 0 || issueCount > 0)
            throw new StageInUseException(s.getName(), mappingCount, issueCount);
        slaTargets.deleteByStage(s.getName());
        stages.delete(s);
    }

    /** Auto-discover safety net: any stage it assigns must exist in the catalog. */
    @Transactional
    public void ensureExists(String name) {
        if (name == null || name.isBlank()) return;
        String trimmed = name.trim();
        if (stages.findByNameIgnoreCase(trimmed).isEmpty()) {
            Stage s = new Stage();
            s.setName(trimmed);
            s.setDisplayOrder(stages.maxDisplayOrder() + 10);
            s.setUpdatedBy("auto-discover");
            stages.save(s);
        }
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Stage name is required");
        String trimmed = name.trim();
        if (trimmed.length() > 64)
            throw new IllegalArgumentException("Stage name must be 64 characters or fewer");
        return trimmed;
    }

    private static String validCategoryOrDefault(String category) {
        return category != null && CATEGORIES.contains(category) ? category : "in-progress";
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> out = new HashMap<>();
        for (Object[] r : rows) out.put((String) r[0], ((Number) r[1]).longValue());
        return out;
    }
}
