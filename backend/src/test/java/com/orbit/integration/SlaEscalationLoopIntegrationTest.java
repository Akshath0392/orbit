package com.orbit.integration;

import com.orbit.domain.agent.AgentToolCall;
import com.orbit.domain.client.Client;
import com.orbit.domain.client.Portfolio;
import com.orbit.domain.client.Project;
import com.orbit.domain.config.StageSlaTarget;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.integration.slack.SlackService;
import com.orbit.repository.AgentDecisionLogRepository;
import com.orbit.repository.AgentToolCallRepository;
import com.orbit.repository.ClientRepository;
import com.orbit.repository.CrEscalationRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.PortfolioRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.repository.StageSlaTargetRepository;
import com.orbit.service.agent.HitlApprovalService;
import com.orbit.service.agent.SlaBreachSweep;
import com.orbit.service.ai.AiGatewayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end proof of the SLA-breach escalation loop through the real
 * Spring context: SlaBreachSweep → real AgentRuntime → real ToolRegistry/
 * SlackSendChannelTool → real HitlApprovalService.approve. Only the outermost
 * effects are mocked — SlackService (no real workspace post) and AiGateway (no
 * real AI call). Proves the HITL gate actually holds: the escalation is NOT sent
 * until a human approves.
 *
 * No @Transactional (see ClientPortfolioIntegrationTest) — explicit JDBC cleanup.
 */
@SpringBootTest
@ActiveProfiles("test")
class SlaEscalationLoopIntegrationTest {

    private static final String KEY = "ITSLA-1";
    private static final String STAGE = "IT-SLA-STAGE";

    @Autowired SlaBreachSweep sweep;
    @Autowired HitlApprovalService hitl;
    @Autowired PortfolioRepository portfolios;
    @Autowired ClientRepository clients;
    @Autowired ProjectRepository projects;
    @Autowired StageSlaTargetRepository targets;
    @Autowired JiraIssueRepository issues;
    @Autowired AgentToolCallRepository toolCalls;
    @Autowired AgentDecisionLogRepository decisions;
    @Autowired CrEscalationRepository ledger;
    @Autowired JdbcTemplate jdbc;

    @MockBean SlackService slack;
    @MockBean AiGatewayService ai; // concrete impl of AiGateway — mocking it satisfies both injection points

    @BeforeEach
    void setUp() {
        cleanup();

        Portfolio pod = new Portfolio();
        pod.setName("IT SLA POD");
        pod.setActive(true);
        pod = portfolios.save(pod);

        Client client = new Client();
        client.setName("IT SLA Client");
        client.setCode("ITSLA");
        client.setActive(true);
        client = clients.save(client);

        Project project = new Project();
        project.setName("IT SLA Project");
        project.setClient(client);
        project.setPortfolio(pod);
        project.setOpsModel("bau");
        project.setActive(true);
        projects.save(project);

        StageSlaTarget t = new StageSlaTarget();
        t.setStage(STAGE);
        t.setTargetDays(5);
        t.setUpdatedAt(LocalDateTime.now());
        targets.save(t);

        JiraIssue cr = new JiraIssue();
        cr.setIssueKey(KEY);
        cr.setIssueType("CR");
        cr.setLifecycleStage(STAGE);
        cr.setCreatedAt(LocalDateTime.now().minusDays(30)); // age 30 vs 5-day target → breached
        cr.setAssigneeName("IT Owner");
        cr.setClient(client);
        cr.setProject(project);
        issues.save(cr);

        ReflectionTestUtils.setField(sweep, "enabled", true);
        ReflectionTestUtils.setField(sweep, "cooldownDays", 3);
        ReflectionTestUtils.setField(sweep, "nearMarginPct", 0);

        when(ai.complete(any(), any())).thenReturn("Escalation: CR " + KEY + " is past its SLA. Please review and unblock.");
        when(slack.resolveChannel(any())).thenReturn(Optional.of("C-TEST"));
        when(slack.sendToChannelDetailed(any(), any())).thenReturn(Map.of("ok", true, "ts", "1.0"));
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void breachIsProposedGatedByHitlAndSentOnlyOnApproval() {
        // 1. Sweep detects the breach and proposes — drafts the nudge and runs the
        //    escalation definition so slack.send_channel is QUEUED, not sent.
        sweep.sweep();

        // 2. The HITL gate holds: nothing has been posted to the channel yet.
        verify(slack, never()).sendToChannelDetailed(any(), any());

        // 3. Dedup ledger stamped for this CR.
        assertThat(ledger.findById(KEY)).isPresent();

        // 4. The escalation is parked as an AWAITING_HITL slack.send_channel step
        //    carrying our CR, drafted message and all.
        AgentToolCall step = toolCalls.findPendingHitl().stream()
            .filter(tc -> "slack.send_channel".equals(tc.getToolName()))
            .filter(tc -> tc.getArgs() != null && tc.getArgs().contains(KEY))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no AWAITING_HITL slack.send_channel step for " + KEY));
        assertThat(step.getHitlOutcome()).isEqualTo("AWAITING_HITL");

        // 5. A human approves → NOW, and only now, the message is sent, and the
        //    decision is audit-logged.
        hitl.approve(step.getRunId(), step.getId(), null, "admin@orbit.io");

        verify(slack, times(1)).sendToChannelDetailed(eq("C-TEST"), contains(KEY));
        // Audit trail: an APPROVED decision log for this CR, decided by our approver.
        // (HitlApprovalService stores agentName as "Agent #<id>" and the proposal as the tool args.)
        assertThat(decisions.findAll())
            .anyMatch(d -> "APPROVED".equals(d.getOutcome())
                && "admin@orbit.io".equals(d.getDecidedBy())
                && d.getProposalJson() != null && d.getProposalJson().contains(KEY));
    }

    private void cleanup() {
        // Agent run records the sweep created reference our project — clear them first (FK-safe order).
        jdbc.update("DELETE FROM agent_decision_log WHERE proposal_json::text LIKE '%ITSLA%'");
        jdbc.update("DELETE FROM agent_tool_calls WHERE run_id IN "
            + "(SELECT id FROM agent_runs WHERE project_id IN (SELECT id FROM projects WHERE name = 'IT SLA Project'))");
        jdbc.update("DELETE FROM agent_runs WHERE project_id IN (SELECT id FROM projects WHERE name = 'IT SLA Project')");
        jdbc.update("DELETE FROM cr_escalation WHERE issue_key LIKE 'ITSLA%'");
        jdbc.update("DELETE FROM jira_issues WHERE issue_key LIKE 'ITSLA%'");
        jdbc.update("DELETE FROM projects WHERE name = 'IT SLA Project'");
        jdbc.update("DELETE FROM clients WHERE code = 'ITSLA'");
        jdbc.update("DELETE FROM portfolios WHERE name = 'IT SLA POD'");
        jdbc.update("DELETE FROM stage_sla_targets WHERE stage = ?", STAGE);
    }
}
