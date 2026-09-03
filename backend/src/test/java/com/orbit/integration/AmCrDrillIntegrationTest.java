package com.orbit.integration;

import com.orbit.domain.client.Client;
import com.orbit.domain.client.Portfolio;
import com.orbit.domain.client.Project;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.ClientRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.PortfolioRepository;
import com.orbit.repository.ProjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: the AM CR drill (`GET /api/v1/am/crs`) 500'd when
 * called without a clientName filter — Hibernate bound the null inside
 * TRIM(:clientName) as bytea and Postgres failed with btrim(bytea) (42883).
 * This exercises the real query against Postgres with clientName=null and an
 * smOwner filter (the owner-share drill path that triggered it).
 *
 * No @Transactional — explicit JDBC cleanup (see SlaEscalationLoopIntegrationTest).
 */
@SpringBootTest
@ActiveProfiles("test")
class AmCrDrillIntegrationTest {

    private static final String KEY = "CRDRL-1";
    private static final String SM = "Sagar BP";

    @Autowired PortfolioRepository portfolios;
    @Autowired ClientRepository clients;
    @Autowired ProjectRepository projects;
    @Autowired JiraIssueRepository issues;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        cleanup();

        Portfolio pod = new Portfolio();
        pod.setName("CR Drill POD");
        pod.setActive(true);
        pod = portfolios.save(pod);

        Client client = new Client();
        client.setName("CR Drill Client");
        client.setCode("CRDRL");
        client.setActive(true);
        client = clients.save(client);

        Project project = new Project();
        project.setName("CR Drill Project");
        project.setClient(client);
        project.setPortfolio(pod);
        project.setOpsModel("bau");
        project.setActive(true);
        projects.save(project);

        JiraIssue cr = new JiraIssue();
        cr.setIssueKey(KEY);
        cr.setIssueType("CR");
        cr.setLifecycleStage("Staging");
        cr.setCreatedAt(LocalDateTime.now().minusDays(5));
        cr.setAssigneeName("Dev A");
        cr.setSmOwner(SM);
        cr.setClient(client);
        cr.setProject(project);
        issues.save(cr);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void drillBySmOwnerWithoutClientNameDoesNotThrowAndReturnsTheCr() {
        // clientName == null is the case that triggered btrim(bytea).
        Page<JiraIssue> page = issues.findAmCrDrill(
            null, null, null, null, null, SM, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent())
            .extracting(JiraIssue::getIssueKey)
            .contains(KEY);
    }

    @Test
    void drillWithAnExplicitClientNameStillMatchesTrimmed() {
        Page<JiraIssue> page = issues.findAmCrDrill(
            null, null, "CR Drill Client", null, null, SM, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent())
            .extracting(JiraIssue::getIssueKey)
            .contains(KEY);
    }

    private void cleanup() {
        jdbc.update("DELETE FROM jira_issues WHERE issue_key LIKE 'CRDRL%'");
        jdbc.update("DELETE FROM projects WHERE name = 'CR Drill Project'");
        jdbc.update("DELETE FROM clients WHERE code = 'CRDRL'");
        jdbc.update("DELETE FROM portfolios WHERE name = 'CR Drill POD'");
    }
}
