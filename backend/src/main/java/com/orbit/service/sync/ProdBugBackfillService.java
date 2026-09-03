package com.orbit.service.sync;

import com.orbit.domain.client.Project;
import com.orbit.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Historical re-routing of prod bugs already in the DB. Runs the standard
 * Jira sync path in "full" mode against a shared prod-bug project — that path
 * already applies the routing branch, so quarantine gets populated for missing
 * codes and correctly-coded issues get their {@code client_id} rewritten.
 *
 * <p>Kept as its own service so admins hit a distinct endpoint (with a
 * telemetry-friendly {@code source=BACKFILL} audit hook later) rather than
 * conflating with the normal Jira sync trigger.
 */
@Service
public class ProdBugBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ProdBugBackfillService.class);

    private final ProjectRepository projects;
    private final JiraSyncService jiraSyncService;

    public ProdBugBackfillService(ProjectRepository projects, JiraSyncService jiraSyncService) {
        this.projects = projects;
        this.jiraSyncService = jiraSyncService;
    }

    /**
     * Re-runs a full Jira sync for the shared prod-bug project so every
     * existing row is re-evaluated by {@link ProdBugRoutingService}.
     */
    public Map<String, Object> backfill(Long projectId) {
        Project p = projects.findById(projectId).orElse(null);
        if (p == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "NOT_FOUND");
            err.put("error", "Project " + projectId + " not found");
            return err;
        }
        if (!p.isSharedProdBugs()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "REJECTED");
            err.put("error", "Project " + p.getName() + " is not marked as a shared prod-bug pool");
            return err;
        }
        log.info("ProdBugBackfill: re-syncing shared project id={} name={}", p.getId(), p.getName());
        Map<String, Object> result = jiraSyncService.trigger("full", projectId);
        result.put("source", "BACKFILL");
        return result;
    }
}
