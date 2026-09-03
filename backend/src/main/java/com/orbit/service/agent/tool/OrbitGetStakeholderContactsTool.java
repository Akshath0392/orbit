package com.orbit.service.agent.tool;

import com.orbit.repository.AppUserRepository;
import com.orbit.repository.ClientRepository;
import com.orbit.repository.ProjectRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OrbitGetStakeholderContactsTool implements AgentTool {

    private final ProjectRepository projects;
    private final ClientRepository clients;
    private final AppUserRepository users;

    public OrbitGetStakeholderContactsTool(ProjectRepository projects,
                                            ClientRepository clients,
                                            AppUserRepository users) {
        this.projects = projects;
        this.clients = clients;
        this.users = users;
    }

    @Override public String id()            { return "orbit.get_stakeholder_contacts"; }
    @Override public String description()   { return "Client and internal stakeholder contact list for escalation"; }
    @Override public boolean requiresHitl() { return false; }

    @Override
    public Map<String, Object> execute(Map<String, Object> args, AgentRunContext ctx) {
        Long projectId = ctx != null ? ctx.getProjectId() : null;

        List<Map<String, Object>> internal = new ArrayList<>();
        // Live roles: V62 collapsed HEAD_PJM into PM, so the old "HEAD_PJM" filter
        // matched no one (audit M7).
        List<String> roles = List.of("PM", "ADMIN");
        users.findAll().stream()
            .filter(u -> roles.contains(u.getRole()))
            .forEach(u -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", u.getName());
                m.put("email", u.getEmail());
                m.put("role", u.getRole());
                internal.add(m);
            });

        List<Map<String, Object>> clientContacts = new ArrayList<>();
        if (projectId != null) {
            projects.findById(projectId).ifPresent(p -> {
                if (p.getClient() != null) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("clientName", p.getClient().getName());
                    m.put("clientCode", p.getClient().getCode());
                    clientContacts.add(m);
                }
            });
        } else {
            clients.findAll().forEach(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("clientName", c.getName());
                m.put("clientCode", c.getCode());
                clientContacts.add(m);
            });
        }

        return Map.of(
            "internalContacts", internal,
            "clientContacts", clientContacts
        );
    }
}
