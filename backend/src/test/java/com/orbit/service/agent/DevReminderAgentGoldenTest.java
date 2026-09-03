package com.orbit.service.agent;

import com.orbit.domain.client.Project;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.service.agent.tool.AgentRunContext;
import com.orbit.service.agent.tool.SlackSendChannelTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DevReminderAgentGoldenTest {

    JiraIssueRepository issues;
    ProjectRepository projects;
    SlackSendChannelTool slackTool;
    DevReminderAgent agent;

    @BeforeEach
    void setUp() {
        issues = mock(JiraIssueRepository.class);
        projects = mock(ProjectRepository.class);
        slackTool = mock(SlackSendChannelTool.class);
        agent = new DevReminderAgent(issues, projects, slackTool);
        when(slackTool.execute(any(), any())).thenReturn(Map.of("ok", true));
    }

    private static JiraIssue issue(String key, String summary, String assignee, String stage) {
        JiraIssue i = new JiraIssue();
        i.setIssueKey(key);
        i.setSummary(summary);
        i.setAssigneeName(assignee);
        i.setLifecycleStage(stage);
        return i;
    }

    private static Project project(Long id, String name) {
        Project p = new Project();
        ReflectionTestUtils.setField(p, "id", id);
        p.setName(name);
        p.setActive(true);
        return p;
    }

    @Test
    void groups_overdue_items_by_assignee_and_sends_to_slack_tool() {
        Project p = project(7L, "Atlas");
        when(projects.findByActiveTrue()).thenReturn(List.of(p));
        when(issues.findOverdueByProjectId(eq(7L), any(LocalDateTime.class))).thenReturn(List.of(
            issue("AT-1", "Fix login", "Rajan", "In dev"),
            issue("AT-2", "Add metrics", "Rajan", "In dev"),
            issue("AT-3", "Refactor module", "Kavya", "In QA")
        ));

        agent.run();

        ArgumentCaptor<Map<String, Object>> argsCap = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<AgentRunContext> ctxCap = ArgumentCaptor.forClass(AgentRunContext.class);
        verify(slackTool).execute(argsCap.capture(), ctxCap.capture());

        String message = (String) argsCap.getValue().get("message");
        assertThat(message).contains("Atlas", "3 items", "*Rajan* (2)", "*Kavya* (1)", "AT-1", "AT-3");
        assertThat(ctxCap.getValue().getProjectId()).isEqualTo(7L);
    }

    @Test
    void no_overdue_skips_slack_send() {
        Project p = project(7L, "Atlas");
        when(projects.findByActiveTrue()).thenReturn(List.of(p));
        when(issues.findOverdueByProjectId(eq(7L), any(LocalDateTime.class))).thenReturn(List.of());
        agent.run();
        verifyNoInteractions(slackTool);
    }
}
