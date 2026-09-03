package com.orbit.controller;

import com.orbit.domain.uat.UatCycle;
import com.orbit.repository.UatCycleRepository;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/uat")
public class UatController {

    private final UatCycleRepository cycles;
    public UatController(UatCycleRepository cycles) { this.cycles = cycles; }

    @GetMapping("/cycles")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Page<Map<String,Object>> list(
            @RequestParam(required=false) Long clientId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return cycles.findByClientId(clientId, PageRequest.of(page, size)).map(c -> {
            var issue = c.getIssue();
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("cycleId", c.getId());
            m.put("issueKey", issue.getIssueKey());
            m.put("crSummary", issue.getSummary() != null ? issue.getSummary() : "");
            m.put("cycleNumber", c.getCycleNumber());
            m.put("startedAt", c.getStartedAt() != null ? c.getStartedAt().toString() : "");
            m.put("signOffStatus", c.getSignOffStatus());
            m.put("signedOffBy", c.getSignedOffBy() != null ? c.getSignedOffBy() : "");
            m.put("envSnapshot", c.getEnvSnapshot() != null ? c.getEnvSnapshot() : "");
            m.put("client", issue.getClient() != null ? issue.getClient().getName() : "");
            return m;
        });
    }

    @GetMapping("/sign-off-status")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String,Object> signOffStatus(@RequestParam(required=false) Long clientId) {
        long signedOff = clientId != null ? cycles.countByIssueClientIdAndSignOffStatus(clientId, "SIGNED_OFF") : cycles.countBySignOffStatus("SIGNED_OFF");
        long rejected  = clientId != null ? cycles.countByIssueClientIdAndSignOffStatus(clientId, "REJECTED")   : cycles.countBySignOffStatus("REJECTED");
        long pending   = clientId != null ? cycles.countByIssueClientIdAndSignOffStatus(clientId, "PENDING")    : cycles.countBySignOffStatus("PENDING");
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("signedOff", signedOff); r.put("rejected", rejected);
        r.put("pending", pending); r.put("total", signedOff + rejected + pending);
        return r;
    }

    @PostMapping("/cycles/{id}/sign-off")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> signOff(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        return cycles.findById(id).map(c -> {
            c.setSignOffStatus((String)body.get("status"));
            c.setSignedOffBy((String)body.getOrDefault("signedOffBy","PJM"));
            c.setNotes((String)body.getOrDefault("notes",""));
            c.setSignedOffAt(LocalDateTime.now());
            cycles.save(c);
            Map<String,Object> r = new LinkedHashMap<>();
            r.put("cycleId", c.getId()); r.put("status", c.getSignOffStatus());
            return ResponseEntity.ok(r);
        }).orElse(ResponseEntity.notFound().build());
    }
}
