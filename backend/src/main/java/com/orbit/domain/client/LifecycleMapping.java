package com.orbit.domain.client;

import jakarta.persistence.*;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name="lifecycle_mappings")
public class LifecycleMapping {

    // issue_type must use the sync vocabulary (JiraSyncService.mapIssueType)
    // or the wildcard ALL — the stage-map lookup key is an exact string match, so a row
    // saved under a display label ("Bug", "UAT Bug") never overrides anything.
    public static final Set<String> CANONICAL_TYPES = Set.of("CR", "PROD_BUG", "UAT_BUG", "TASK", "OTHER", "ALL");

    private static final Map<String, String> LEGACY_LABELS = Map.of(
        "bug", "PROD_BUG", "production bug", "PROD_BUG", "uat defect", "UAT_BUG");

    /** Canonical form of an issue-type label, or null when unrecognized. */
    public static String canonicalType(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (CANONICAL_TYPES.contains(t)) return t;
        String upper = t.toUpperCase(Locale.ROOT).replace(' ', '_');
        if (CANONICAL_TYPES.contains(upper)) return upper;
        return LEGACY_LABELS.get(t.toLowerCase(Locale.ROOT));
    }

    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String jiraStatus;
    private String issueType;
    private String gaugeStage;

    public LifecycleMapping() {}
    public Long getId() { return id; }
    public String getJiraStatus() { return jiraStatus; }
    public void setJiraStatus(String v) { this.jiraStatus=v; }
    public String getIssueType() { return issueType; }
    public void setIssueType(String v) { this.issueType=v; }
    public String getGaugeStage() { return gaugeStage; }
    public void setGaugeStage(String v) { this.gaugeStage=v; }

    private Integer displayOrder;
    private String  category;   // backlog | in-progress | qa | uat | blocked | ready | released | closed
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer v) { this.displayOrder=v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category=v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private LifecycleMapping m = new LifecycleMapping();
        public Builder jiraStatus(String v) { m.jiraStatus=v; return this; }
        public Builder issueType(String v) { m.issueType=v; return this; }
        public Builder gaugeStage(String v) { m.gaugeStage=v; return this; }
        public LifecycleMapping build() { return m; }
    }
}
