package com.orbit.integration.jira;

import com.orbit.domain.config.JiraConfig;
import com.orbit.domain.issue.JiraIssue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JiraFieldMapperTest {

    private static JiraConfig cfg() {
        JiraConfig c = new JiraConfig();
        c.setStoryPointsField("customfield_10016");
        c.setSprintField("customfield_10007");
        c.setSmField("customfield_11000");
        c.setPjmField("customfield_11001");
        return c;
    }

    @Test
    void mapsStoryPointsUserPickersAndCloudSprintArray() {
        JiraIssue issue = new JiraIssue();
        JiraFieldMapper.apply(issue, Map.of(
            "customfield_10016", 5.0,
            "customfield_11000", Map.of("displayName", "Sonia"),
            "customfield_11001", "Prakash",
            "customfield_10007", List.of(
                Map.of("id", 41, "name", "S27", "state", "closed"),
                Map.of("id", 42, "name", "S28", "state", "active"))
        ), cfg());

        assertThat(issue.getStoryPoints()).isEqualByComparingTo("5.0");
        assertThat(issue.getSmOwner()).isEqualTo("Sonia");
        assertThat(issue.getPjmOwner()).isEqualTo("Prakash");
        assertThat(issue.getCurrentSprintId()).isEqualTo(42L);
        assertThat(issue.getCurrentSprintName()).isEqualTo("S28");
    }

    @Test
    void parsesLegacyGreenhopperSprintStrings() {
        JiraIssue issue = new JiraIssue();
        JiraFieldMapper.apply(issue, Map.of(
            "customfield_10007", List.of(
                "com.atlassian.greenhopper.service.sprint.Sprint@1a[id=7,rapidViewId=3,state=CLOSED,name=Sprint 7]",
                "com.atlassian.greenhopper.service.sprint.Sprint@1b[id=8,rapidViewId=3,state=ACTIVE,name=Sprint 8]")
        ), cfg());

        assertThat(issue.getCurrentSprintId()).isEqualTo(8L);
        assertThat(issue.getCurrentSprintName()).isEqualTo("Sprint 8");
    }

    @Test
    void unmappedConfigOrMissingValuesLeaveIssueUntouched() {
        JiraIssue issue = new JiraIssue();
        JiraFieldMapper.apply(issue, Map.of("summary", "x"), new JiraConfig());
        JiraFieldMapper.apply(issue, Map.of("customfield_10016", "not-a-number"), cfg());

        assertThat(issue.getStoryPoints()).isNull();
        assertThat(issue.getSmOwner()).isNull();
        assertThat(issue.getCurrentSprintId()).isNull();
    }
}
