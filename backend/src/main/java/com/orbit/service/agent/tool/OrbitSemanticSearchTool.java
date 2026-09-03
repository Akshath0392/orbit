package com.orbit.service.agent.tool;

import com.orbit.repository.JiraIssueRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrbitSemanticSearchTool implements AgentTool {

    private final JiraIssueRepository issues;

    public OrbitSemanticSearchTool(JiraIssueRepository issues) { this.issues = issues; }

    @Override public String id()            { return "orbit.semantic_search_past_issues"; }
    @Override public String description()   { return "Search past issues by keyword similarity"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        String query = String.valueOf(args.getOrDefault("query", "")).trim();
        if (query.isBlank()) return Map.of("results", List.of(), "query", "");

        int limit = 5;
        try { limit = Integer.parseInt(String.valueOf(args.getOrDefault("limit", "5"))); } catch (Exception ignored) {}

        List<Map<String, Object>> results = issues.searchBySummaryKeyword(
                "%" + query.replace(" ", "%") + "%", PageRequest.of(0, limit))
            .stream()
            .map(i -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", i.getIssueKey());
                m.put("summary", i.getSummary());
                m.put("lifecycleStage", i.getLifecycleStage());
                m.put("issueType", i.getIssueType());
                m.put("client", i.getClient() != null ? i.getClient().getName() : null);
                m.put("updatedAt", i.getUpdatedAt() != null ? i.getUpdatedAt().toString() : null);
                return m;
            }).collect(Collectors.toList());

        return Map.of("query", query, "results", results, "count", results.size(),
            "note", "keyword search — pgvector semantic search available after EmbeddingService is wired");
    }
}
