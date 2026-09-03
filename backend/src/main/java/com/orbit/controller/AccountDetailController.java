package com.orbit.controller;

import com.orbit.domain.account.GovernanceMeeting;
import com.orbit.domain.account.ProjectRisk;
import com.orbit.domain.account.ProjectTeam;
import com.orbit.domain.account.ProjectWin;
import com.orbit.repository.GovernanceMeetingRepository;
import com.orbit.repository.ProjectRepository;
import com.orbit.repository.ProjectRiskRepository;
import com.orbit.repository.ProjectTeamRepository;
import com.orbit.repository.ProjectWinRepository;
import com.orbit.service.AccountDetailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountDetailController {

    private final AccountDetailService        service;
    private final ProjectRepository           projects;
    private final ProjectTeamRepository       teams;
    private final ProjectRiskRepository       risks;
    private final ProjectWinRepository        wins;
    private final GovernanceMeetingRepository governance;

    public AccountDetailController(AccountDetailService service,
                                    ProjectRepository projects,
                                    ProjectTeamRepository teams,
                                    ProjectRiskRepository risks,
                                    ProjectWinRepository wins,
                                    GovernanceMeetingRepository governance) {
        this.service = service; this.projects = projects;
        this.teams = teams; this.risks = risks;
        this.wins = wins; this.governance = governance;
    }

    // ── Aggregated detail view ────────────────────────────────────────────────
    @GetMapping("/{projectId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> detail(@PathVariable Long projectId) {
        return service.assemble(projectId)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Sprint Scope — phase-grouped delivery tracker (W18) ──────────────────
    @GetMapping("/{projectId}/sprint-scope")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> sprintScope(@PathVariable Long projectId) {
        return service.sprintScope(projectId)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Team contacts ─────────────────────────────────────────────────────────
    @PutMapping("/{projectId}/team")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> saveTeam(@PathVariable Long projectId,
                                       @RequestBody Map<String,Object> body,
                                       HttpServletRequest req) {
        if (projects.findById(projectId).isEmpty()) return ResponseEntity.notFound().build();
        ProjectTeam t = teams.findByProjectId(projectId).orElseGet(() -> {
            ProjectTeam nt = new ProjectTeam(); nt.setProjectId(projectId); return nt;
        });
        if (body.containsKey("internalPm"))      t.setInternalPm((String) body.get("internalPm"));
        if (body.containsKey("internalAm"))      t.setInternalAm((String) body.get("internalAm"));
        if (body.containsKey("internalEm"))      t.setInternalEm((String) body.get("internalEm"));
        if (body.containsKey("internalSol"))     t.setInternalSol((String) body.get("internalSol"));
        if (body.containsKey("internalTechLead")) t.setInternalTechLead((String) body.get("internalTechLead"));
        if (body.containsKey("internalQaLead"))   t.setInternalQaLead((String) body.get("internalQaLead"));
        if (body.containsKey("internalSupportMgr")) t.setInternalSupportMgr((String) body.get("internalSupportMgr"));
        if (body.containsKey("clientSponsor"))   t.setClientSponsor((String) body.get("clientSponsor"));
        if (body.containsKey("clientTechSpoc"))  t.setClientTechSpoc((String) body.get("clientTechSpoc"));
        if (body.containsKey("clientBizSpoc"))   t.setClientBizSpoc((String) body.get("clientBizSpoc"));
        if (body.containsKey("clientPm"))        t.setClientPm((String) body.get("clientPm"));
        t.setUpdatedAt(LocalDateTime.now());
        t.setUpdatedBy(req.getUserPrincipal() != null ? req.getUserPrincipal().getName() : "unknown");
        teams.save(t);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Risk register ─────────────────────────────────────────────────────────
    @PostMapping("/{projectId}/risks")
    @PreAuthorize("hasAnyRole('PM','ADMIN','CSM')")
    public ResponseEntity<?> addRisk(@PathVariable Long projectId,
                                      @RequestBody Map<String,Object> body,
                                      HttpServletRequest req) {
        if (projects.findById(projectId).isEmpty()) return ResponseEntity.notFound().build();
        ProjectRisk r = new ProjectRisk();
        r.setProjectId(projectId);
        r.setRisk((String) body.get("risk"));
        r.setJiraTicket((String) body.get("jiraTicket"));
        r.setRag((String) body.get("rag"));
        r.setActionOwner((String) body.get("actionOwner"));
        r.setSource((String) body.get("source"));
        if (body.get("receivedOn") != null) r.setReceivedOn(LocalDate.parse(body.get("receivedOn").toString()));
        if (body.get("actionEnd")  != null) r.setActionEnd(LocalDate.parse(body.get("actionEnd").toString()));
        r.setCreatedBy(req.getUserPrincipal() != null ? req.getUserPrincipal().getName() : "unknown");
        risks.save(r);
        return ResponseEntity.ok(Map.of("id", r.getId()));
    }

    @DeleteMapping("/{projectId}/risks/{id}")
    @PreAuthorize("hasAnyRole('PM','ADMIN','CSM')")
    public ResponseEntity<?> deleteRisk(@PathVariable Long projectId, @PathVariable Long id) {
        risks.findById(id)
            .filter(r -> r.getProjectId().equals(projectId))
            .ifPresent(risks::delete);
        return ResponseEntity.noContent().build();
    }

    // ── Wins register ─────────────────────────────────────────────────────────
    @PostMapping("/{projectId}/wins")
    @PreAuthorize("hasAnyRole('PM','ADMIN','CSM')")
    public ResponseEntity<?> addWin(@PathVariable Long projectId,
                                     @RequestBody Map<String,Object> body,
                                     HttpServletRequest req) {
        if (projects.findById(projectId).isEmpty()) return ResponseEntity.notFound().build();
        ProjectWin w = new ProjectWin();
        w.setProjectId(projectId);
        w.setWin((String) body.get("win"));
        w.setSource((String) body.get("source"));
        if (body.get("recognisedOn") != null) w.setRecognisedOn(LocalDate.parse(body.get("recognisedOn").toString()));
        w.setCreatedBy(req.getUserPrincipal() != null ? req.getUserPrincipal().getName() : "unknown");
        wins.save(w);
        return ResponseEntity.ok(Map.of("id", w.getId()));
    }

    @DeleteMapping("/{projectId}/wins/{id}")
    @PreAuthorize("hasAnyRole('PM','ADMIN','CSM')")
    public ResponseEntity<?> deleteWin(@PathVariable Long projectId, @PathVariable Long id) {
        wins.findById(id)
            .filter(w -> w.getProjectId().equals(projectId))
            .ifPresent(wins::delete);
        return ResponseEntity.noContent().build();
    }

    // ── Governance meetings ───────────────────────────────────────────────────
    @PostMapping("/{projectId}/governance")
    @PreAuthorize("hasAnyRole('PM','ADMIN','CSM')")
    public ResponseEntity<?> addGovernance(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        if (projects.findById(projectId).isEmpty()) return ResponseEntity.notFound().build();
        GovernanceMeeting g = new GovernanceMeeting();
        g.setProjectId(projectId);
        g.setCadence((String) body.get("cadence"));
        g.setTitle((String) body.get("title"));
        g.setOwner((String) body.get("owner"));
        g.setStatus((String) body.get("status"));
        g.setNotes((String) body.get("notes"));
        if (body.get("lastHeld") != null) g.setLastHeld(LocalDate.parse(body.get("lastHeld").toString()));
        if (body.get("nextDue")  != null) g.setNextDue(LocalDate.parse(body.get("nextDue").toString()));
        governance.save(g);
        return ResponseEntity.ok(Map.of("id", g.getId()));
    }

    @DeleteMapping("/{projectId}/governance/{id}")
    @PreAuthorize("hasAnyRole('PM','ADMIN','CSM')")
    public ResponseEntity<?> deleteGovernance(@PathVariable Long projectId, @PathVariable Long id) {
        governance.findById(id)
            .filter(g -> projectId.equals(g.getProjectId()))
            .ifPresent(governance::delete);
        return ResponseEntity.noContent().build();
    }

    // ── Account metadata (type, revenue, contract end) ────────────────────────
    @PutMapping("/{projectId}/metadata")
    @PreAuthorize("hasAnyRole('PM','ADMIN','CSM')")
    public ResponseEntity<?> setMetadata(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        return projects.findById(projectId).map(p -> {
            if (body.containsKey("accountType"))     p.setAccountType((String) body.get("accountType"));
            if (body.containsKey("revenueExposure") && body.get("revenueExposure") != null)
                p.setRevenueExposure(new BigDecimal(body.get("revenueExposure").toString()));
            if (body.containsKey("contractEndDate") && body.get("contractEndDate") != null)
                p.setContractEndDate(LocalDate.parse(body.get("contractEndDate").toString()));
            projects.save(p);
            return ResponseEntity.ok((Object) Map.of("ok", true));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Ops model ─────────────────────────────────────────────────────────────
    @PutMapping("/{projectId}/ops-model")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> setOpsModel(@PathVariable Long projectId, @RequestBody Map<String,Object> body) {
        return projects.findById(projectId).map(p -> {
            String mode = (String) body.get("opsModel");
            if (mode == null || !java.util.List.of("launch","bau","launch+bau").contains(mode))
                return ResponseEntity.badRequest().body(Map.of("error", "invalid opsModel"));
            p.setOpsModel(mode);
            projects.save(p);
            return ResponseEntity.ok((Object) Map.of("opsModel", mode));
        }).orElse(ResponseEntity.notFound().build());
    }
}
