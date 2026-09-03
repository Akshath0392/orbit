package com.orbit.service.sync;

import com.orbit.domain.client.Client;
import com.orbit.domain.client.Project;
import com.orbit.domain.issue.JiraIssue;
import com.orbit.domain.routing.ProdBugQuarantine;
import com.orbit.repository.ClientRepository;
import com.orbit.repository.ProdBugQuarantineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Contract tests for the shared prod-bug router. Keep these tests pure —
 * no Spring context — so the routing rules stay obvious.
 */
class ProdBugRoutingServiceTest {

    private ClientRepository clients;
    private ProdBugQuarantineRepository quarantine;
    private ProdBugRoutingService svc;

    private final Project sharedProject = new Project();

    @BeforeEach
    void setUp() {
        clients = mock(ClientRepository.class);
        quarantine = mock(ProdBugQuarantineRepository.class);
        svc = new ProdBugRoutingService(clients, quarantine);
        sharedProject.setName("POOL");
        sharedProject.setSharedProdBugs(true);
        sharedProject.setClientCodeField("customfield_11683");
    }

    private JiraIssue issue(String key) {
        JiraIssue i = new JiraIssue();
        i.setIssueKey(key);
        return i;
    }

    private Client client(long id, String code) {
        Client c = new Client();
        ReflectionTestUtils.setField(c, "id", id);
        ReflectionTestUtils.setField(c, "code", code);
        return c;
    }

    // ── normaliseCode ────────────────────────────────────────────────────

    @Test
    void normaliseCode_handlesStringMapAndBlanks() {
        assertEquals("ACME", ProdBugRoutingService.normaliseCode("ACME"));
        assertEquals("ACME", ProdBugRoutingService.normaliseCode("  ACME  "));
        assertNull(ProdBugRoutingService.normaliseCode(""));
        assertNull(ProdBugRoutingService.normaliseCode("   "));
        assertNull(ProdBugRoutingService.normaliseCode(null));

        Map<String, Object> select = Map.of("value", "BEETLE", "id", 1);
        assertEquals("BEETLE", ProdBugRoutingService.normaliseCode(select));

        Map<String, Object> nameOnly = Map.of("name", "Citrus");
        assertEquals("Citrus", ProdBugRoutingService.normaliseCode(nameOnly));
    }

    // ── happy path ───────────────────────────────────────────────────────

    @Test
    void route_setsClientAndClearsPriorQuarantine_whenCodeMatches() {
        Client acme = client(10L, "ACME");
        when(clients.findActiveByCodeIgnoreCase("acme")).thenReturn(Optional.of(acme));

        ProdBugQuarantine prior = new ProdBugQuarantine();
        prior.setJiraKey("POOL-1");
        prior.setReason(ProdBugQuarantine.Reason.UNKNOWN_CODE);
        prior.setSeenAt(LocalDateTime.now().minusDays(1));
        prior.setLastSeenAt(LocalDateTime.now().minusHours(1));
        when(quarantine.findByJiraKey("POOL-1")).thenReturn(Optional.of(prior));

        JiraIssue i = issue("POOL-1");
        svc.route(i, "acme", sharedProject);

        assertSame(acme, i.getClient());
        assertNotNull(prior.getResolvedAt());
        assertEquals("auto:code-now-matches", prior.getResolvedBy());
    }

    @Test
    void route_setsClient_forSelectFieldPayload() {
        Client acme = client(10L, "ACME");
        when(clients.findActiveByCodeIgnoreCase("ACME")).thenReturn(Optional.of(acme));

        JiraIssue i = issue("POOL-2");
        svc.route(i, Map.of("value", "ACME"), sharedProject);

        assertSame(acme, i.getClient());
    }

    // ── quarantine paths ────────────────────────────────────────────────

    @Test
    void route_quarantinesAndNullsClient_whenCodeMissing() {
        JiraIssue i = issue("POOL-3");
        i.setClient(client(99L, "IRRELEVANT"));

        svc.route(i, null, sharedProject);

        assertNull(i.getClient());
        ArgumentCaptor<ProdBugQuarantine> cap = ArgumentCaptor.forClass(ProdBugQuarantine.class);
        verify(quarantine).save(cap.capture());
        assertEquals(ProdBugQuarantine.Reason.MISSING_CODE, cap.getValue().getReason());
        assertNull(cap.getValue().getRawClientCode());
    }

    @Test
    void route_quarantinesUnknownCode_recordingRawValue() {
        when(clients.findActiveByCodeIgnoreCase("XYZ")).thenReturn(Optional.empty());

        JiraIssue i = issue("POOL-4");
        svc.route(i, "XYZ", sharedProject);

        assertNull(i.getClient());
        ArgumentCaptor<ProdBugQuarantine> cap = ArgumentCaptor.forClass(ProdBugQuarantine.class);
        verify(quarantine).save(cap.capture());
        assertEquals(ProdBugQuarantine.Reason.UNKNOWN_CODE, cap.getValue().getReason());
        assertEquals("XYZ", cap.getValue().getRawClientCode());
    }

    @Test
    void route_quarantinesCodeHeldOnlyByInactiveClient() {
        // A retired duplicate row keeping its code must not capture
        // bugs — even though the inactive-inclusive lookup would match it.
        Client retired = client(72L, "OLDCO");
        ReflectionTestUtils.setField(retired, "active", false);
        lenient().when(clients.findByCodeIgnoreCase("OLDCO")).thenReturn(Optional.of(retired));
        when(clients.findActiveByCodeIgnoreCase("OLDCO")).thenReturn(Optional.empty());

        JiraIssue i = issue("POOL-12");
        svc.route(i, "OLDCO", sharedProject);

        assertNull(i.getClient());
        ArgumentCaptor<ProdBugQuarantine> cap = ArgumentCaptor.forClass(ProdBugQuarantine.class);
        verify(quarantine).save(cap.capture());
        assertEquals(ProdBugQuarantine.Reason.UNKNOWN_CODE, cap.getValue().getReason());
        assertEquals("OLDCO", cap.getValue().getRawClientCode());
    }

    @Test
    void route_bumpsLastSeen_forRepeatedStuckIssue() {
        ProdBugQuarantine existing = new ProdBugQuarantine();
        existing.setJiraKey("POOL-5");
        existing.setReason(ProdBugQuarantine.Reason.MISSING_CODE);
        LocalDateTime oldSeen = LocalDateTime.now().minusHours(6);
        existing.setSeenAt(oldSeen);
        existing.setLastSeenAt(oldSeen);
        when(quarantine.findByJiraKey("POOL-5")).thenReturn(Optional.of(existing));

        JiraIssue i = issue("POOL-5");
        svc.route(i, null, sharedProject);

        verify(quarantine).save(existing);
        verify(quarantine, never()).save(argThat(q -> q != existing));
        assertTrue(existing.getLastSeenAt().isAfter(oldSeen));
        assertEquals(oldSeen, existing.getSeenAt(), "seenAt is set once and should not move");
    }

    @Test
    void route_reopensResolvedQuarantine_ifBugStuckAgain() {
        ProdBugQuarantine existing = new ProdBugQuarantine();
        existing.setJiraKey("POOL-6");
        existing.setReason(ProdBugQuarantine.Reason.UNKNOWN_CODE);
        existing.setResolvedAt(LocalDateTime.now().minusDays(2));
        existing.setResolvedBy("admin@example.com");
        existing.setResolutionNote("Was a typo, fixed the Jira field.");
        when(quarantine.findByJiraKey("POOL-6")).thenReturn(Optional.of(existing));

        JiraIssue i = issue("POOL-6");
        svc.route(i, null, sharedProject);

        assertNull(existing.getResolvedAt());
        assertNull(existing.getResolvedBy());
        assertNull(existing.getResolutionNote());
    }

    // ── code lookup is case-insensitive ────────────────────────────────

    @Test
    void route_matchesCodeCaseInsensitively() {
        Client acme = client(10L, "ACME");
        when(clients.findActiveByCodeIgnoreCase("Acme")).thenReturn(Optional.of(acme));

        JiraIssue i = issue("POOL-7");
        svc.route(i, "Acme", sharedProject);

        assertSame(acme, i.getClient());
    }

    @Test
    void route_doesNotClearAlreadyResolvedQuarantine_whenMatchSucceeds() {
        Client acme = client(10L, "ACME");
        when(clients.findActiveByCodeIgnoreCase("ACME")).thenReturn(Optional.of(acme));

        ProdBugQuarantine resolved = new ProdBugQuarantine();
        resolved.setJiraKey("POOL-8");
        resolved.setResolvedAt(LocalDateTime.now().minusDays(1));
        resolved.setResolvedBy("admin@example.com");
        when(quarantine.findByJiraKey("POOL-8")).thenReturn(Optional.of(resolved));

        JiraIssue i = issue("POOL-8");
        svc.route(i, "ACME", sharedProject);

        assertSame(acme, i.getClient());
        verify(quarantine, never()).save(any(ProdBugQuarantine.class));
    }

    // ── does not touch quarantine on happy path when none existed ─────

    @Test
    void route_matchesAndDoesNotWriteQuarantine_whenNonePriorExisted() {
        Client acme = client(10L, "ACME");
        when(clients.findActiveByCodeIgnoreCase("ACME")).thenReturn(Optional.of(acme));
        when(quarantine.findByJiraKey("POOL-9")).thenReturn(Optional.empty());

        JiraIssue i = issue("POOL-9");
        svc.route(i, "ACME", sharedProject);

        verify(quarantine, never()).save(any(ProdBugQuarantine.class));
    }

    // ── fields map is untouched (regression against setSeenAt on repeats) ─

    @Test
    void route_doesNotBackdateSeenAtOnRepeats() {
        ProdBugQuarantine existing = new ProdBugQuarantine();
        existing.setJiraKey("POOL-10");
        existing.setReason(ProdBugQuarantine.Reason.MISSING_CODE);
        LocalDateTime firstSeen = LocalDateTime.of(2026, 5, 1, 9, 0);
        existing.setSeenAt(firstSeen);
        existing.setLastSeenAt(firstSeen);
        when(quarantine.findByJiraKey("POOL-10")).thenReturn(Optional.of(existing));

        svc.route(issue("POOL-10"), null, sharedProject);

        assertEquals(firstSeen, existing.getSeenAt());
    }

    // Empty map = no value present ------------------------------------

    @Test
    void route_treatsEmptyMapAsMissingCode() {
        JiraIssue i = issue("POOL-11");
        svc.route(i, new HashMap<String, Object>(), sharedProject);

        assertNull(i.getClient());
        ArgumentCaptor<ProdBugQuarantine> cap = ArgumentCaptor.forClass(ProdBugQuarantine.class);
        verify(quarantine).save(cap.capture());
        assertEquals(ProdBugQuarantine.Reason.MISSING_CODE, cap.getValue().getReason());
    }
}
