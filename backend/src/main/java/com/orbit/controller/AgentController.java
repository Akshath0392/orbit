package com.orbit.controller;

import com.orbit.domain.agent.AgentDecisionLog;
import com.orbit.repository.AgentDecisionLogRepository;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final AgentDecisionLogRepository log;
    public AgentController(AgentDecisionLogRepository log) { this.log = log; }

    @GetMapping("/decisions")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Page<Map<String,Object>> decisions(
            @RequestParam(required=false) String agentName,
            @RequestParam(required=false) String outcome,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size) {
        return log.findFiltered(agentName, outcome, PageRequest.of(page, size)).map(d -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("agent", d.getAgentName() != null ? d.getAgentName() : "");
            m.put("trigger", d.getTriggerEvent() != null ? d.getTriggerEvent() : "");
            m.put("proposal", d.getProposalJson() != null ? d.getProposalJson() : "{}");
            m.put("outcome", d.getOutcome() != null ? d.getOutcome() : "");
            m.put("outcomeNote", d.getOutcomeNote() != null ? d.getOutcomeNote() : "");
            m.put("tokensUsed", d.getTokensUsed() != null ? d.getTokensUsed() : 0);
            m.put("by", d.getDecidedBy() != null ? d.getDecidedBy() : "");
            m.put("at", d.getDecidedAt() != null ? d.getDecidedAt().toString() : "");
            return m;
        });
    }

    @GetMapping("/cost-summary")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String,Object> costSummary(@RequestParam(defaultValue="week") String period) {
        LocalDateTime since = "month".equals(period)
            ? LocalDateTime.now().minusDays(30) : LocalDateTime.now().minusDays(7);
        Long tokens = log.sumTokensSince(since);
        double cost = tokens * 0.003 / 1000.0;
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("tokensTotal", tokens);
        r.put("estimatedCostUsd", Math.round(cost * 100.0) / 100.0);
        r.put("period", period);
        return r;
    }

    @PostMapping("/proposals/{id}/approve")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> approve(@PathVariable Long id, Authentication auth) {
        return log.findById(id).map(d -> {
            d.setOutcome("APPROVED");
            d.setDecidedBy(auth.getName());
            d.setDecidedAt(LocalDateTime.now());
            log.save(d);
            Map<String,Object> r = new LinkedHashMap<>();
            r.put("id", id); r.put("outcome","APPROVED");
            return ResponseEntity.ok(r);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/proposals/{id}/reject")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> reject(@PathVariable Long id,
                                     @RequestBody Map<String,String> body,
                                     Authentication auth) {
        String reason = body.get("reason");
        if (reason == null || reason.isBlank()) {
            Map<String,Object> err = new LinkedHashMap<>();
            err.put("error","reason is required for rejection");
            return ResponseEntity.badRequest().body(err);
        }
        return log.findById(id).map(d -> {
            d.setOutcome("REJECTED");
            d.setOutcomeNote(reason);
            d.setDecidedBy(auth.getName());
            d.setDecidedAt(LocalDateTime.now());
            log.save(d);
            Map<String,Object> r = new LinkedHashMap<>();
            r.put("id", id); r.put("outcome","REJECTED");
            return ResponseEntity.ok(r);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/proposals/{id}/edit")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> edit(@PathVariable Long id,
                                   @RequestBody Map<String,Object> body,
                                   Authentication auth) {
        return log.findById(id).map(d -> {
            d.setOutcome("EDITED");
            d.setDecidedBy(auth.getName());
            d.setDecidedAt(LocalDateTime.now());
            log.save(d);
            Map<String,Object> r = new LinkedHashMap<>();
            r.put("id", id); r.put("outcome","EDITED");
            return ResponseEntity.ok(r);
        }).orElse(ResponseEntity.notFound().build());
    }
}
