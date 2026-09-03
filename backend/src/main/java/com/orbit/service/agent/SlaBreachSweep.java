package com.orbit.service.agent;

import com.orbit.domain.agent.AgentDefinition;
import com.orbit.domain.agent.CrEscalation;
import com.orbit.domain.config.StageSlaTarget;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.repository.AgentDefinitionRepository;
import com.orbit.repository.CrEscalationRepository;
import com.orbit.repository.JiraIssueRepository;
import com.orbit.repository.LeaveRecordRepository;
import com.orbit.repository.StageSlaTargetRepository;
import com.orbit.service.ai.AiGateway;
import com.orbit.service.am.SlaBucketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The detection half of the standing SLA-breach escalation loop.
 *
 * On a schedule it sweeps open CRs, classifies each against its stage SLA target
 * with the canonical {@link SlaBucketService} (so it escalates on the same
 * definition the dashboard shows), and returns the ones worth a
 * human's attention: breached (or, if configured, near-margin) CRs that have not
 * already been proposed within the cooldown window. Owner availability is
 * correlated so an owner-on-leave breach reads as higher urgency.
 *
 * Wave 2 (this class) does detection + dedup only — the scheduled method logs
 * candidates and sends nothing. Wave 3 wires each candidate through the
 * tool-based HITL pipeline (slack.send_channel → Slack approval card). HITL is
 * mandatory for the outbound step (CLAUDE.md rule 3).
 */
@Service
public class SlaBreachSweep {

    private static final Logger log = LoggerFactory.getLogger(SlaBreachSweep.class);

    /** System AgentDefinition (find-or-seed) that carries the HITL-gated slack.send_channel tool. */
    static final String DEF_NAME = "SLA Breach Escalation";

    private final JiraIssueRepository issues;
    private final StageSlaTargetRepository targets;
    private final CrEscalationRepository ledger;
    private final LeaveRecordRepository leaves;
    private final SlaBucketService sla;
    private final AgentRuntime runtime;
    private final AgentDefinitionRepository agentDefs;
    private final AiGateway ai;

    @Value("${orbit.agents.sla-escalation.enabled:false}")
    private boolean enabled;
    @Value("${orbit.agents.sla-escalation.cooldown-days:3}")
    private int cooldownDays;
    /** 0 = breached only. >0 also escalates NEAR CRs within this % of their target (closest to breach). */
    @Value("${orbit.agents.sla-escalation.near-margin-pct:0}")
    private int nearMarginPct;

    public SlaBreachSweep(JiraIssueRepository issues,
                          StageSlaTargetRepository targets,
                          CrEscalationRepository ledger,
                          LeaveRecordRepository leaves,
                          SlaBucketService sla,
                          AgentRuntime runtime,
                          AgentDefinitionRepository agentDefs,
                          AiGateway ai) {
        this.issues = issues;
        this.targets = targets;
        this.ledger = ledger;
        this.leaves = leaves;
        this.sla = sla;
        this.runtime = runtime;
        this.agentDefs = agentDefs;
        this.ai = ai;
    }

    /** A CR the sweep judges escalation-worthy, with the context a draft needs. */
    public record Candidate(String issueKey, Long projectId, String client, String stage, String owner,
                            long ageDays, int targetDays, SlaBucketService.Bucket bucket,
                            boolean ownerOnLeave) {
        public String urgency() {
            if (bucket != SlaBucketService.Bucket.BREACHED) return "LOW";
            return ownerOnLeave ? "HIGH" : "MEDIUM";
        }
    }

    @Scheduled(cron = "${orbit.agents.sla-escalation.cron:0 30 * * * *}")
    public void sweep() {
        if (!enabled) return;
        LocalDateTime now = LocalDateTime.now();
        List<Candidate> candidates = findCandidates(now);
        if (candidates.isEmpty()) return;
        AgentDefinition def = escalationDefinition();
        for (Candidate c : candidates) propose(def, c, now);
        log.info("SlaBreachSweep: proposed HITL escalations for {} CR(s): {}",
            candidates.size(), candidates.stream().map(Candidate::issueKey).toList());
    }

    /**
     * Turn one candidate into a HITL proposal: draft the nudge, run the escalation
     * definition so its slack.send_channel tool is queued AWAITING_HITL (never sent
     * here — a human approves via the Slack card), then stamp the dedup ledger.
     */
    private void propose(AgentDefinition def, Candidate c, LocalDateTime now) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("message", draftMessage(c));
        input.put("issueKey", c.issueKey());
        if (c.client() != null) input.put("client", c.client());
        input.put("urgency", c.urgency());
        // AgentRuntime queues slack.send_channel as AWAITING_HITL (requiresHitl) and
        // publishes HitlAwaitingEvent → SlackHitlBridge posts the approval card. The
        // send happens only when a human approves (HitlApprovalService). Rule 3.
        runtime.execute(def, c.projectId(), "CRON", input, "SCHEDULED");

        CrEscalation row = ledger.findById(c.issueKey())
            .orElseGet(() -> new CrEscalation(c.issueKey(), now));
        row.setLastProposedAt(now);
        ledger.save(row);
    }

    private AgentDefinition escalationDefinition() {
        return agentDefs.findByName(DEF_NAME).orElseGet(() -> {
            AgentDefinition d = new AgentDefinition();
            d.setName(DEF_NAME);
            d.setDescription("Standing SLA-breach escalation — drafts a Slack nudge for a breaching CR. "
                + "HITL-gated (slack.send_channel), never auto-sends.");
            d.setAgentType("SYSTEM");
            d.setTriggerType("SCHEDULED");
            d.setTools(new String[]{"slack.send_channel"});
            d.setRequiresHitl(true);
            d.setEnabled(true);
            d.setSystemAgent(true);
            d.setCreatedBy("system");
            d.setCreatedAt(LocalDateTime.now());
            d.setUpdatedAt(LocalDateTime.now());
            return agentDefs.save(d);
        });
    }

    /** Draft the Slack nudge via the AI gateway, with a deterministic fallback so a
     *  missing/failed AI provider never blocks a real escalation. */
    private String draftMessage(Candidate c) {
        String system = """
            You are Orbit's SLA escalation agent. Draft a concise Slack nudge for a delivery CR
            that is breaching (or close to breaching) its stage SLA. Address the project team.
            State the CR, client, stage, how far past target, the owner, and a clear recommended
            next step. Under 100 words, plain text (no markdown), professional and non-alarmist.
            """;
        String user = String.format(
            "CR %s for client %s is %s its SLA at stage \"%s\" (age %d days vs %d-day target). "
            + "Owner: %s%s. Urgency: %s.",
            c.issueKey(), c.client(), c.bucket(), c.stage(), c.ageDays(), c.targetDays(),
            c.owner() == null ? "unassigned" : c.owner(),
            c.ownerOnLeave() ? " (on leave today)" : "", c.urgency());
        try {
            String draft = ai.complete(system, user);
            if (draft != null && !draft.isBlank()) return draft.strip();
        } catch (Exception e) {
            log.warn("SlaBreachSweep: AI draft failed for {} — using template. {}", c.issueKey(), e.getMessage());
        }
        return fallbackMessage(c);
    }

    private static String fallbackMessage(Candidate c) {
        return String.format(
            "SLA %s: CR %s (%s) at stage \"%s\" is %d days old vs a %d-day target. Owner: %s%s. "
            + "Please review and unblock or re-plan.",
            c.bucket(), c.issueKey(), c.client() == null ? "unknown client" : c.client(), c.stage(),
            c.ageDays(), c.targetDays(), c.owner() == null ? "unassigned" : c.owner(),
            c.ownerOnLeave() ? " (on leave today)" : "");
    }

    /**
     * Pure detection core (unit-tested): breaching (and optionally near-margin) open CRs
     * not in cooldown, with owner + today's availability attached. Sends nothing.
     */
    public List<Candidate> findCandidates(LocalDateTime now) {
        Map<String, Integer> targetByStage = targets.findAll().stream()
            .collect(Collectors.toMap(StageSlaTarget::getStage, StageSlaTarget::getTargetDays, (a, b) -> a));
        LocalDateTime cutoff = now.minusDays(cooldownDays);
        Set<String> inCooldown = ledger.findByLastProposedAtGreaterThanEqual(cutoff).stream()
            .map(CrEscalation::getIssueKey).collect(Collectors.toSet());
        Set<String> onLeave = namesOnLeave(now.toLocalDate());

        List<Candidate> out = new ArrayList<>();
        for (JiraIssue cr : issues.findOpenCrsForEscalation()) {
            String key = cr.getIssueKey();
            if (key == null || inCooldown.contains(key)) continue;
            Integer target = cr.getLifecycleStage() == null ? null : targetByStage.get(cr.getLifecycleStage());
            long age = cr.getCreatedAt() == null ? 0 : ChronoUnit.DAYS.between(cr.getCreatedAt(), now);
            SlaBucketService.Bucket bucket = sla.classify(age, target);
            if (bucket == null) continue;                 // untracked stage (Hold/Unstaged) — never escalate
            if (!isWorthy(bucket, age, target)) continue; // near but outside the margin
            String owner = firstNonBlank(cr.getAssigneeName(), cr.getSmOwner(), cr.getPjmOwner());
            boolean ownerLeave = owner != null && onLeave.contains(owner);
            out.add(new Candidate(key,
                cr.getProject() == null ? null : cr.getProject().getId(),
                cr.getClient() == null ? null : cr.getClient().getName(),
                cr.getLifecycleStage(), owner, age, target, bucket, ownerLeave));
        }
        return out;
    }

    private boolean isWorthy(SlaBucketService.Bucket bucket, long age, int target) {
        if (bucket == SlaBucketService.Bucket.BREACHED) return true;
        if (bucket == SlaBucketService.Bucket.NEAR && nearMarginPct > 0) {
            return age >= target * (1 - nearMarginPct / 100.0);
        }
        return false;
    }

    private Set<String> namesOnLeave(LocalDate day) {
        return leaves.findByStartDateBetweenOrderByStartDateAsc(day, day).stream()
            .map(l -> l.getUser() != null ? l.getUser().getName() : l.getDarwinEmpId())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
