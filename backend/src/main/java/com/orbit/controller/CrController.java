package com.orbit.controller;

import com.orbit.domain.issue.*;
import com.orbit.repository.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/cr")
public class CrController {

    private final JiraIssueRepository issues;
    private final IssueMilestoneRepository milestones;
    private final IssueNoteRepository notes;
    private final ProjectRepository projectRepo;
    private final LifecycleMappingRepository lifecycle;

    public CrController(JiraIssueRepository issues, IssueMilestoneRepository milestones,
                        IssueNoteRepository notes, ProjectRepository projectRepo,
                        LifecycleMappingRepository lifecycle) {
        this.issues = issues; this.milestones = milestones; this.notes = notes;
        this.projectRepo = projectRepo; this.lifecycle = lifecycle;
    }

    @GetMapping("/stages")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String,Object>> stages(@RequestParam(defaultValue="CR") String issueType) {
        // Deduplicate on gaugeStage (multiple Jira statuses can map to the same lifecycle stage).
        Map<String,Map<String,Object>> seen = new LinkedHashMap<>();
        for (var m : lifecycle.findOrderedByIssueType(issueType)) {
            String stage = m.getGaugeStage();
            if (stage == null) continue;
            seen.computeIfAbsent(stage, s -> {
                Map<String,Object> r = new LinkedHashMap<>();
                r.put("name",         stage);
                r.put("displayOrder", m.getDisplayOrder() != null ? m.getDisplayOrder() : 50);
                r.put("category",     m.getCategory()     != null ? m.getCategory()     : "in-progress");
                return r;
            });
        }
        return new ArrayList<>(seen.values());
    }

    @GetMapping("/aging-buckets")
    @PreAuthorize("isAuthenticated()")
    public Map<String,Long> agingBuckets(@RequestParam(required=false) Long portfolioId) {
        List<Long> projectIds = null;
        if (portfolioId != null) {
            projectIds = projectRepo.findByPortfolioIdAndActiveTrue(portfolioId)
                .stream().map(p -> p.getId()).collect(Collectors.toList());
            if (projectIds.isEmpty()) {
                Map<String,Long> empty = new LinkedHashMap<>();
                empty.put("0_3", 0L); empty.put("4_7", 0L);
                empty.put("8_14", 0L); empty.put("15p", 0L);
                return empty;
            }
        }
        Map<String,Long> out = new LinkedHashMap<>();
        out.put("0_3", 0L); out.put("4_7", 0L); out.put("8_14", 0L); out.put("15p", 0L);
        for (Object[] r : issues.countOpenCrsByAgingBucket(projectIds)) {
            out.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        }
        return out;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Page<Map<String,Object>> list(
            @RequestParam(required=false) Long clientId,
            @RequestParam(required=false) Long portfolioId,
            @RequestParam(required=false) String stage,
            @RequestParam(required=false) String sm,
            @RequestParam(required=false) String pjm,
            @RequestParam(required=false) String type,
            @RequestParam(required=false) String search,
            @RequestParam(defaultValue="issueKey") String sort,
            @RequestParam(defaultValue="asc") String direction,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {

        String sortCol = switch (sort) {
            case "age", "createdAt" -> "createdAt";
            case "stage", "lifecycleStage" -> "lifecycleStage";
            case "priority" -> "priority";
            case "owner", "assigneeName" -> "assigneeName";
            case "client" -> "client.name";
            default -> "issueKey";
        };
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable p = PageRequest.of(page, size, Sort.by(dir, sortCol));

        String searchLike = (search != null && !search.isBlank())
            ? "%" + search.toLowerCase() + "%" : null;
        // type LAUNCH|BAU matches 'launch+bau' projects too (AM drill semantics)
        String typeLike = (type != null && !type.isBlank())
            ? "%" + type.toLowerCase() + "%" : null;

        return issues.findCrsFiltered(clientId, stage, portfolioId,
                emptyToNull(sm), emptyToNull(pjm), typeLike, searchLike, p)
            .map(this::toCrResponse);
    }

    private static String emptyToNull(String v) { return v == null || v.isBlank() ? null : v; }

    /** Select options for the CR board filter bar (mock: pod/sm/pjm/type). */
    @GetMapping("/filter-options")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String,Object> filterOptions() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("sms", issues.findDistinctCrSmOwners());
        out.put("pjms", issues.findDistinctCrPjmOwners());
        return out;
    }

    @GetMapping("/stage-summary")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public Map<String,Long> stageSummary(@RequestParam(required=false) Long clientId,
                                          @RequestParam(required=false) Long portfolioId) {
        List<Object[]> rows;
        if (portfolioId != null) {
            List<Long> projectIds = projectRepo.findByPortfolioIdAndActiveTrue(portfolioId)
                .stream().map(proj -> proj.getId()).collect(Collectors.toList());
            rows = projectIds.isEmpty()
                ? issues.countCrsByStage(clientId)          // fallback: show all CRs
                : issues.countCrsByStageForProjects(projectIds);
        } else {
            rows = issues.countCrsByStage(clientId);
        }
        Map<String,Long> result = new LinkedHashMap<>();
        rows.stream()
            .filter(r -> r[0] != null)
            .sorted((a, b) -> Long.compare((Long) b[1], (Long) a[1]))
            .forEach(r -> result.put((String) r[0], (Long) r[1]));
        return result;
    }

    @GetMapping("/{issueKey}")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> detail(@PathVariable String issueKey) {
        return issues.findByIssueKey(issueKey)
            .map(issue -> {
                List<IssueMilestone> ms = milestones.findByIssueId(issue.getId());
                List<IssueNote> noteList = notes.findByIssueIdOrderByCreatedAtDesc(issue.getId());
                Map<String,Object> r = new LinkedHashMap<>(toCrResponse(issue));
                r.put("milestones", ms.stream().map(m -> {
                    Map<String,Object> mMap = new LinkedHashMap<>();
                    mMap.put("type",   m.getMilestoneType());
                    mMap.put("isTbc",  Boolean.TRUE.equals(m.getIsTbc()));
                    mMap.put("status", m.getStatus() != null ? m.getStatus() : "TBC");
                    mMap.put("targetDate", m.getTargetDate() != null ? m.getTargetDate().toString() : null);
                    return mMap;
                }).collect(Collectors.toList()));
                r.put("notes", noteList.stream().map(n -> {
                    Map<String,Object> nMap = new LinkedHashMap<>();
                    nMap.put("id", n.getId()); nMap.put("text", n.getText());
                    nMap.put("clientSafe", Boolean.TRUE.equals(n.getIsClientSafe()));
                    nMap.put("by", n.getCreatedBy()); nMap.put("at", n.getCreatedAt());
                    return nMap;
                }).collect(Collectors.toList()));
                return ResponseEntity.ok(r);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{issueKey}/notes")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<?> addNote(@PathVariable String issueKey,
                                      @RequestBody Map<String,Object> body,
                                      Authentication auth) {
        return issues.findByIssueKey(issueKey).map(issue -> {
            IssueNote n = IssueNote.builder()
                .issue(issue)
                .text((String) body.get("text"))
                .isClientSafe(Boolean.TRUE.equals(body.get("isClientSafe")))
                .createdBy(auth.getName())
                .build();
            notes.save(n);
            return ResponseEntity.ok(Map.of("id", n.getId(), "text", n.getText()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('PM','ADMIN')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required=false) Long clientId,
            @RequestParam(required=false) Long portfolioId,
            @RequestParam(required=false) String stage,
            @RequestParam(required=false) String sm,
            @RequestParam(required=false) String pjm,
            @RequestParam(required=false) String type,
            @RequestParam(required=false) String search,
            @RequestParam(defaultValue="issueKey") String sort,
            @RequestParam(defaultValue="asc") String direction) {

        String sortCol = switch (sort) {
            case "age", "createdAt" -> "createdAt";
            case "stage", "lifecycleStage" -> "lifecycleStage";
            case "priority" -> "priority";
            case "owner", "assigneeName" -> "assigneeName";
            case "client" -> "client.name";
            default -> "issueKey";
        };
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable all = PageRequest.of(0, 10_000, Sort.by(dir, sortCol));

        String searchLike = (search != null && !search.isBlank()) ? "%" + search.toLowerCase() + "%" : null;
        String typeLike = (type != null && !type.isBlank()) ? "%" + type.toLowerCase() + "%" : null;

        List<Map<String,Object>> rows = issues.findCrsFiltered(clientId, stage, portfolioId,
                emptyToNull(sm), emptyToNull(pjm), typeLike, searchLike, all)
            .stream().map(this::toCrResponse).collect(Collectors.toList());

        // mock column order: CR, Client, Description, Status, Stage, POD, SM, PjM, Type, Aging
        StringBuilder csv = new StringBuilder("CR,Client,Description,Status,Stage,POD,SM,PjM,Type,Aging\n");
        for (Map<String,Object> r : rows) {
            csv.append(escapeCsv(r.get("key")))
               .append(',').append(escapeCsv(r.get("client")))
               .append(',').append(escapeCsv(r.get("summary")))
               .append(',').append(escapeCsv(r.get("jiraStatus")))
               .append(',').append(escapeCsv(r.get("stage")))
               .append(',').append(escapeCsv(r.get("pod")))
               .append(',').append(escapeCsv(r.get("sm")))
               .append(',').append(escapeCsv(r.get("pjm")))
               .append(',').append(escapeCsv(r.get("type")))
               .append(',').append(escapeCsv(r.get("age")))
               .append('\n');
        }

        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cr-export.csv\"")
            .body(bytes);
    }

    private String escapeCsv(Object val) {
        return com.orbit.util.Csv.escape(val);
    }

    private Map<String,Object> toCrResponse(JiraIssue i) {
        String stage  = i.getLifecycleStage() != null ? i.getLifecycleStage() : "Unknown";
        boolean onHold = "Hold".equalsIgnoreCase(stage);
        String risk   = onHold ? "critical" : "ok";
        String rl     = onHold ? "On hold · needs follow-up"
                       : stage.contains("Closed") || stage.contains("Released") ? "Delivered"
                       : "Active";

        Map<String,Object> m = new LinkedHashMap<>();
        m.put("key",        i.getIssueKey());
        m.put("summary",    i.getSummary() != null ? i.getSummary() : "");
        m.put("stage",      stage);
        m.put("jiraStatus", i.getJiraStatus() != null ? i.getJiraStatus() : stage);
        m.put("pri",        i.getPriority()     != null ? i.getPriority()     : "—");
        m.put("owner",      i.getAssigneeName() != null ? i.getAssigneeName() : "—");
        m.put("risk",       risk);
        m.put("rl",         rl);
        m.put("age",        daysSince(i.getCreatedAt()) + "d");
        m.put("clientId",   i.getClient() != null ? i.getClient().getId()   : null);
        m.put("client",     i.getClient() != null ? i.getClient().getName() : "");
        // mock board columns: POD · SM · PjM · Type
        var project = i.getProject();
        m.put("pod",  project != null && project.getPortfolio() != null ? project.getPortfolio().getName() : "—");
        m.put("sm",   i.getSmOwner()  != null ? i.getSmOwner()  : "—");
        m.put("pjm",  i.getPjmOwner() != null ? i.getPjmOwner() : "—");
        m.put("type", project == null || project.getOpsModel() == null ? "—" : project.getOpsModel().toUpperCase());
        return m;
    }

    private long daysSince(java.time.LocalDateTime dt) {
        if (dt == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(dt, java.time.LocalDateTime.now());
    }
}
