package com.orbit.service.sync;

import com.orbit.domain.client.Project;
import com.orbit.domain.config.JiraConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /search/jql only returns requested fields, so every configured jira_config
 * mapping must appear in the request list — an omission silently nulls the
 * mapped column on every JQL-synced issue while the field mapper itself
 * (JiraFieldMapper) stays correct.
 */
class JiraSyncServiceRequestedFieldsTest {

    private static JiraSyncService service() {
        return new JiraSyncService(null, null, null, null, null, null, null, null, null);
    }

    private static JiraConfig fullConfig() {
        JiraConfig cfg = new JiraConfig();
        cfg.setSlaField("customfield_sla");
        cfg.setStoryPointsField("customfield_sp");
        cfg.setSprintField("customfield_sprint");
        cfg.setSmField("customfield_sm");
        cfg.setPjmField("customfield_pjm");
        cfg.setDeveloperField("customfield_11502");
        return cfg;
    }

    @Test
    void everyConfiguredMappingIsRequested() {
        List<String> fields = service().requestedFields(fullConfig(), new Project());

        assertThat(fields).contains(
            "customfield_sla", "customfield_sp", "customfield_sprint",
            "customfield_sm", "customfield_pjm", "customfield_11502");
        // Core fields every consumer depends on.
        assertThat(fields).contains("summary", "status", "issuetype", "assignee",
            "reporter", "created", "updated", "resolutiondate");
    }

    @Test
    void configuredDeveloperFieldIsRequested() {
        JiraConfig cfg = new JiraConfig();
        cfg.setDeveloperField("customfield_11502");

        assertThat(service().requestedFields(cfg, new Project()))
            .contains("customfield_11502");
    }

    @Test
    void blankMappingsAreNotRequested() {
        List<String> fields = service().requestedFields(new JiraConfig(), new Project());

        assertThat(fields).noneMatch(f -> f == null || f.isBlank());
        assertThat(fields).noneMatch(f -> f.startsWith("customfield_"));
    }

    @Test
    void clientCodeFieldRequestedOnlyForRoutingProjects() {
        Project pool = new Project();
        pool.setSharedProdBugs(true);
        pool.setClientCodeField("customfield_11683");

        JiraSyncService routingOn = service();
        ReflectionTestUtils.setField(routingOn, "prodBugRoutingEnabled", true);
        assertThat(routingOn.requestedFields(fullConfig(), pool)).contains("customfield_11683");

        // Routing disabled → the pool project's client-code field is not fetched.
        assertThat(service().requestedFields(fullConfig(), pool)).doesNotContain("customfield_11683");
    }
}
