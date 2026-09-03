package com.orbit.service.agent.tool;

import com.orbit.integration.jira.JiraClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JiraCommentTool implements AgentTool {

    private final JiraClient jira;
    public JiraCommentTool(JiraClient jira) { this.jira = jira; }

    @Override public String id()            { return "jira.comment"; }
    @Override public String description()   { return "Add a comment to a Jira issue"; }
    @Override public boolean requiresHitl() { return true; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        String issueKey = String.valueOf(args.getOrDefault("issueKey", ""));
        String comment  = String.valueOf(args.getOrDefault("comment", ""));
        if (issueKey.isBlank()) return Map.of("ok", false, "error", "issueKey_required");
        if (comment.isBlank())  return Map.of("ok", false, "error", "comment_required");
        return jira.addComment(issueKey, comment);
    }
}
