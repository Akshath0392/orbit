package com.orbit.integration.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.client.AppUser;
import com.orbit.domain.client.Portfolio;
import com.orbit.domain.snapshot.SnapshotState;
import com.orbit.repository.PortfolioRepository;
import com.orbit.service.snapshot.SnapshotArgs;
import com.orbit.service.snapshot.SnapshotResult;
import com.orbit.service.snapshot.SnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SnapshotSlackHandlerTest {

    SlackClient slack;
    SnapshotService service;
    PortfolioRepository portfolios;
    SnapshotSlackHandler handler;

    @BeforeEach
    void setUp() {
        slack = mock(SlackClient.class);
        service = mock(SnapshotService.class);
        portfolios = mock(PortfolioRepository.class);
        Portfolio p = new Portfolio();
        ReflectionTestUtils.setField(p, "id", 7L);
        ReflectionTestUtils.setField(p, "name", "Atlas");
        when(portfolios.findByActiveTrue()).thenReturn(List.of(p));
        handler = new SnapshotSlackHandler(slack, service, portfolios, "https://orbit.example");
    }

    @Test
    void matches_only_snapshot_slash_text() {
        assertThat(handler.matchesSlashText("snapshot")).isTrue();
        assertThat(handler.matchesSlashText("Snapshot ")).isTrue();
        assertThat(handler.matchesSlashText("snapshot now")).isTrue();
        assertThat(handler.matchesSlashText("alerts")).isFalse();
        assertThat(handler.matchesSlashText(null)).isFalse();
    }

    @Test
    void open_modal_calls_views_open_with_three_blocks() {
        AppUser u = newUser();
        handler.openModal("trig-123", u);
        ArgumentCaptor<Map<String, Object>> cap = captor();
        verify(slack).openView(eq("trig-123"), cap.capture());
        Map<String, Object> view = cap.getValue();
        assertThat(view).containsEntry("callback_id", "orbit_snapshot");
        List<?> blocks = (List<?>) view.get("blocks");
        assertThat(blocks).hasSize(3);
    }

    @Test
    void open_modal_no_trigger_returns_false_and_does_nothing() {
        assertThat(handler.openModal(null, newUser())).isFalse();
        assertThat(handler.openModal("", newUser())).isFalse();
        verify(slack, never()).openView(any(), any());
    }

    @Test
    void open_modal_works_for_any_role() {
        // Snapshot JWT carries scope=snapshot:read and is elevated to ROLE_ADMIN
        // on GETs by JwtFilter, so any linked Slack user can request any lens.
        for (String role : new String[]{"ADMIN", "PM", "ENGINEERING", "LEADERSHIP", "CSM", "REVENUE"}) {
            AppUser u = newUserWithRole(role);
            assertThat(handler.openModal("trig-" + role, u)).isTrue();
        }
        verify(slack, org.mockito.Mockito.times(6)).openView(any(), any());
    }

    private static AppUser newUserWithRole(String role) {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", 100L);
        u.setEmail("u@orbit.io");
        u.setRole(role);
        return u;
    }

    @Test
    void submission_calls_snapshot_service_and_dms_link() throws Exception {
        when(service.request(any(), any())).thenReturn(SnapshotResult.fresh(42L, SnapshotState.PENDING));
        String json = "{\"state\":{\"values\":{"
            + "\"portfolio_block\":{\"portfolio\":{\"selected_option\":{\"value\":\"7\"}}},"
            + "\"lens_block\":{\"lens\":{\"selected_option\":{\"value\":\"PM\"}}},"
            + "\"project_block\":{\"project\":{\"value\":\"11\"}}"
            + "}}}";
        JsonNode view = new ObjectMapper().readTree(json);

        String link = handler.handleSubmission(view, newUser(), "U999");

        assertThat(link).isEqualTo("https://orbit.example/snapshots/42");
        ArgumentCaptor<SnapshotArgs> argCap = ArgumentCaptor.forClass(SnapshotArgs.class);
        verify(service).request(any(), argCap.capture());
        assertThat(argCap.getValue().portfolioId()).isEqualTo(7L);
        assertThat(argCap.getValue().lens()).isEqualTo("PM");
        assertThat(argCap.getValue().projectId()).isEqualTo(11L);
        verify(slack).postMessage(eq("U999"), any(), any());
    }

    @Test
    void submission_falls_back_to_user_role_when_lens_missing() throws Exception {
        when(service.request(any(), any())).thenReturn(SnapshotResult.fresh(1L, SnapshotState.PENDING));
        JsonNode view = new ObjectMapper().readTree("{\"state\":{\"values\":{}}}");
        handler.handleSubmission(view, newUser(), "U1");
        ArgumentCaptor<SnapshotArgs> argCap = ArgumentCaptor.forClass(SnapshotArgs.class);
        verify(service).request(any(), argCap.capture());
        assertThat(argCap.getValue().lens()).isEqualTo("PM");   // newUser() role
    }

    @Test
    void cached_result_uses_reuse_headline() throws Exception {
        when(service.request(any(), any())).thenReturn(SnapshotResult.cached(5L));
        JsonNode view = new ObjectMapper().readTree("{\"state\":{\"values\":{}}}");
        handler.handleSubmission(view, newUser(), "U1");
        ArgumentCaptor<List<Map<String,Object>>> blocksCap = captor();
        verify(slack).postMessage(eq("U1"), any(), blocksCap.capture());
        String rendered = blocksCap.getValue().toString();
        assertThat(rendered).contains("Reusing a recent snapshot");
        assertThat(rendered).contains("/snapshots/5");
    }

    private static AppUser newUser() {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", 100L);
        u.setEmail("pm@orbit.io");
        u.setRole("PM");
        return u;
    }

    @SuppressWarnings("unchecked")
    private static <T> ArgumentCaptor<T> captor() {
        return (ArgumentCaptor<T>) ArgumentCaptor.forClass(Object.class);
    }
}
