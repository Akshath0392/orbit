package com.orbit.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity: all Flyway migrations apply without errors, and key tables exist.
 */
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;

    @Test
    void allMigrationsAppliedSuccessfully() {
        var info = flyway.info();
        long failed = java.util.Arrays.stream(info.applied())
            .filter(m -> !m.getState().isApplied())
            .count();
        assertThat(failed).isZero();
    }

    @Test
    void latestVersionIsV45() {
        var applied = flyway.info().applied();
        assertThat(applied).isNotEmpty();
        String latestVersion = applied[applied.length - 1].getVersion().toString();
        assertThat(Integer.parseInt(latestVersion)).isGreaterThanOrEqualTo(48);
    }

    @Test
    void coreTablesExist() {
        for (String table : new String[]{
            "clients", "projects", "portfolios",
            "alerts", "app_users", "role_screen_config",
            "leave_records", "hrms_sync_runs", "hrms_config", "jira_sync_runs",
            "man_day_budgets", "man_day_snapshots",
            "developers", "sla_rules", "lifecycle_mappings",
            "jira_config"
        }) {
            Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, table);
            assertThat(count).as("table '%s' should exist", table).isEqualTo(1);
        }
    }

    @Test
    void roleScreenConfigHasFiveDefaultRoles() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM role_screen_config", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(5);
    }

    @Test
    void adminUserExists() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM app_users WHERE email = 'admin@orbit.io'", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
