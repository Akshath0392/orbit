package com.orbit.service.agent.tool;

import com.orbit.integration.jira.JiraClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JiraTransitionTool implements AgentTool {

    private final JiraClient jira;
    public JiraTransitionTool(JiraClient jira) { this.jira = jira; }

    @Override public String id()            { return "jira.transition"; }
    @Override public String description()   { return "Change a Jira issue's status via a workflow transition"; }
    @Override public boolean requiresHitl() { return true; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        String issueKey    = String.valueOf(args.getOrDefault("issueKey", ""));
        String transitionId = String.valueOf(args.getOrDefault("transitionId", ""));
        if (issueKey.isBlank())     return Map.of("ok", false, "error", "issueKey_required");
        if (transitionId.isBlank()) return Map.of("ok", false, "error", "transitionId_required");
        return jira.transition(issueKey, transitionId);
    }
}
