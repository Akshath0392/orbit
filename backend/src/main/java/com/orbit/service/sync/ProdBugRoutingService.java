package com.orbit.service.sync;

import com.orbit.domain.client.Client;
import com.orbit.domain.client.Project;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.domain.routing.ProdBugQuarantine;
import com.orbit.repository.ClientRepository;
import com.orbit.repository.ProdBugQuarantineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Routes issues coming from a shared prod-bug Jira project (one project holds
 * bugs for many clients) to the correct Orbit {@link Client} based on a
 * Jira custom field ({@link Project#getClientCodeField()}).
 *
 * <p>Missing or unknown codes are recorded in {@code prod_bug_quarantine} so
 * an admin can resolve them without losing the bug. The quarantine row is
 * idempotent per Jira key — re-syncing the same stuck bug bumps
 * {@code last_seen_at} instead of duplicating.
 */
@Service
public class ProdBugRoutingService {

    private static final Logger log = LoggerFactory.getLogger(ProdBugRoutingService.class);

    private final ClientRepository clients;
    private final ProdBugQuarantineRepository quarantine;

    public ProdBugRoutingService(ClientRepository clients,
                                 ProdBugQuarantineRepository quarantine) {
        this.clients = clients;
        this.quarantine = quarantine;
    }

    /**
     * Apply routing rules to {@code issue}. Extracts the client code from
     * {@code rawFieldValue} (the raw Jira custom-field payload), resolves
     * to an <em>active</em> {@link Client} case-insensitively (a code left on
     * a retired duplicate row quarantines instead of routing), and either:
     * <ul>
     *   <li>sets {@code issue.client} and clears any stale quarantine row, or</li>
     *   <li>records/refreshes a quarantine row and leaves {@code issue.client} untouched.</li>
     * </ul>
     */
    public void route(JiraIssue issue, Object rawFieldValue, Project sharedProject) {
        String code = normaliseCode(rawFieldValue);

        if (code == null) {
            issue.setClient(null);
            recordQuarantine(issue, null, ProdBugQuarantine.Reason.MISSING_CODE);
            return;
        }

        Optional<Client> match = clients.findActiveByCodeIgnoreCase(code);
        if (match.isPresent()) {
            issue.setClient(match.get());
            clearQuarantineIfPresent(issue.getIssueKey());
        } else {
            issue.setClient(null);
            recordQuarantine(issue, code, ProdBugQuarantine.Reason.UNKNOWN_CODE);
        }
    }

    /**
     * Extracts a string value from Jira's custom-field payload. Jira returns
     * select-list fields as {@code {"value":"ACME"}} and free-text fields as
     * a plain string. Anything else is dropped. Blank strings map to null.
     */
    @SuppressWarnings("unchecked")
    static String normaliseCode(Object raw) {
        if (raw == null) return null;
        String s;
        if (raw instanceof String str) {
            s = str;
        } else if (raw instanceof java.util.Map<?, ?> m) {
            Object v = ((java.util.Map<String, Object>) m).get("value");
            if (v == null) v = ((java.util.Map<String, Object>) m).get("name");
            s = v == null ? null : v.toString();
        } else {
            s = raw.toString();
        }
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private void recordQuarantine(JiraIssue issue, String rawCode, ProdBugQuarantine.Reason reason) {
        String key = issue.getIssueKey();
        Optional<ProdBugQuarantine> existing = quarantine.findByJiraKey(key);
        LocalDateTime now = LocalDateTime.now();
        if (existing.isPresent()) {
            ProdBugQuarantine q = existing.get();
            q.setLastSeenAt(now);
            q.setReason(reason);
            q.setRawClientCode(rawCode);
            // If admin marked it resolved but Jira is still sending it stuck,
            // re-open — otherwise it disappears from the admin queue silently.
            if (q.getResolvedAt() != null) {
                q.setResolvedAt(null);
                q.setResolvedBy(null);
                q.setResolutionNote(null);
            }
            quarantine.save(q);
            return;
        }
        ProdBugQuarantine q = new ProdBugQuarantine();
        q.setJiraIssue(issue.getId() == null ? null : issue);
        q.setJiraKey(key);
        q.setRawClientCode(rawCode);
        q.setReason(reason);
        q.setSeenAt(now);
        q.setLastSeenAt(now);
        quarantine.save(q);
        log.info("ProdBugRouting: quarantined jiraKey={} reason={} rawCode={}", key, reason, rawCode);
    }

    private void clearQuarantineIfPresent(String jiraKey) {
        quarantine.findByJiraKey(jiraKey).ifPresent(q -> {
            if (q.getResolvedAt() == null) {
                q.setResolvedAt(LocalDateTime.now());
                q.setResolvedBy("auto:code-now-matches");
                quarantine.save(q);
                log.info("ProdBugRouting: auto-resolved quarantine jiraKey={}", jiraKey);
            }
        });
    }
}
