package com.orbit.service.agent.tool;

import com.orbit.domain.alert.Alert;
import com.orbit.repository.AlertRepository;
import com.orbit.repository.ProjectRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrbitCreateAlertTool implements AgentTool {

    private final AlertRepository alerts;
    private final ProjectRepository projects;

    public OrbitCreateAlertTool(AlertRepository alerts, ProjectRepository projects) {
        this.alerts = alerts;
        this.projects = projects;
    }

    @Override public String id()          { return "orbit.create_alert"; }
    @Override public String description() { return "Create an Orbit alert visible in Alert Center"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        Alert a = new Alert();
        a.setTitle(str(args, "title", "Agent alert"));
        a.setDetail(str(args, "detail", null));
        a.setSeverity(str(args, "severity", "risk"));
        a.setAlertType(str(args, "alertType", "AGENT_ALERT"));
        a.setPhase(str(args, "phase", null));
        a.setSourceAgent(ctx != null && ctx.getAgentId() != null ? ctx.getAgentId().toString() : "agent");

        Object projectId = args.get("projectId");
        if (projectId != null) {
            Long pid = projectId instanceof Number n ? n.longValue() : Long.parseLong(projectId.toString());
            projects.findById(pid).ifPresent(p -> {
                a.setProject(p);
                a.setClient(p.getClient());
            });
        }

        Alert saved = alerts.save(a);
        return Map.of("status", "created", "alertId", saved.getId(), "title", saved.getTitle());
    }

    private String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString() : def;
    }
}
