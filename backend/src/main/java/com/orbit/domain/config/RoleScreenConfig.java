package com.orbit.domain.config;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "role_screen_config")
public class RoleScreenConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String roleName;
    private String displayName;
    private String screenIds;  // comma-separated

    // Chart presentation preference for dashboard charts:
    // {chartType: line|bar|stacked, breakdownChartType: bar|line, palette:
    // classic|vibrant, runtimeToggle: on|off}; null/absent keys mean defaults.
    // Preference only — rollout lives in the section.charts.config feature flag.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "chart_config", columnDefinition = "jsonb")
    private Map<String, String> chartConfig;

    public RoleScreenConfig() {}
    public Long getId() { return id; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String v) { this.roleName = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public String getScreenIds() { return screenIds; }
    public void setScreenIds(String v) { this.screenIds = v; }
    public Map<String, String> getChartConfig() { return chartConfig; }
    public void setChartConfig(Map<String, String> v) { this.chartConfig = v; }
}
