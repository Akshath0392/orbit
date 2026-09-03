package com.orbit.integration.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.orbit.domain.client.AppUser;
import com.orbit.domain.client.Portfolio;
import com.orbit.repository.PortfolioRepository;
import com.orbit.service.snapshot.SnapshotArgs;
import com.orbit.service.snapshot.SnapshotResult;
import com.orbit.service.snapshot.SnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Slack glue for the snapshot agent:
 *   - opens a 3-select modal (portfolio · lens · project) on {@code /orbit snapshot},
 *   - on view_submission ({@code callback_id="orbit_snapshot"}), enqueues the snapshot
 *     and DMs the user the stable link {@code /snapshots/{id}}.
 *
 * The link itself is the progress tracker — the frontend viewer page polls
 * {@code GET /api/v1/snapshots/{id}/status}, so we never need chat.update.
 */
@Component
public class SnapshotSlackHandler {

    private static final Logger log = LoggerFactory.getLogger(SnapshotSlackHandler.class);
    private static final String CALLBACK_ID = "orbit_snapshot";
    private static final List<String> LENSES = List.of("LEADERSHIP", "ENGINEERING", "PM", "CSM", "REVENUE");

    private final SlackClient slack;
    private final SnapshotService snapshotService;
    private final PortfolioRepository portfolios;
    private final String publicBaseUrl;

    public SnapshotSlackHandler(SlackClient slack,
                                SnapshotService snapshotService,
                                PortfolioRepository portfolios,
                                @Value("${orbit.public-base-url:http://localhost:3000}") String publicBaseUrl) {
        this.slack = slack;
        this.snapshotService = snapshotService;
        this.portfolios = portfolios;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    /** Detects {@code /orbit snapshot ...} (case-insensitive). */
    public boolean matchesSlashText(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.equals("snapshot") || t.startsWith("snapshot ") || t.startsWith("snapshot\t");
    }

    /** Opens the modal. Returns true if accepted. */
    public boolean openModal(String triggerId, AppUser user) {
        if (triggerId == null || triggerId.isBlank()) {
            log.warn("Snapshot modal cannot open: no trigger_id");
            return false;
        }
        List<Portfolio> active = portfolios.findByActiveTrue();
        Map<String, Object> view = buildView(active, user);
        slack.openView(triggerId, view);
        return true;
    }

    /** True iff this view_submission payload is ours. */
    public boolean isOurSubmission(String callbackId) {
        return CALLBACK_ID.equals(callbackId);
    }

    /** Handles a submitted modal. Returns the link DMed to the user. */
    public String handleSubmission(JsonNode view, AppUser user, String slackUserId) {
        JsonNode values = view.path("state").path("values");
        Long portfolioId = selectedLong(values, "portfolio_block", "portfolio");
        String lens      = selectedString(values, "lens_block", "lens");
        Long projectId   = selectedLong(values, "project_block", "project");

        if (lens == null || lens.isBlank()) lens = user.getRole();

        SnapshotArgs args = new SnapshotArgs(SnapshotArgs.KIND_RADAR, portfolioId, lens, projectId);
        SnapshotResult result;
        try {
            result = snapshotService.request(user, args);
        } catch (Exception e) {
            log.warn("Snapshot request failed for user={}: {}", user.getEmail(), e.getMessage());
            slack.postMessage(slackUserId, ":x: Could not start snapshot: " + e.getMessage(), List.of());
            return null;
        }

        String link = publicBaseUrl + "/snapshots/" + result.id();
        String headline;
        if (result.fromCache())   headline = ":white_check_mark: Reusing a recent snapshot — opens instantly.";
        else if (result.dedup())  headline = ":hourglass_flowing_sand: That snapshot is already in progress.";
        else                      headline = ":camera: Snapshot queued. The link below tracks progress.";
        String body = headline + "\n<" + link + "|Open snapshot>";
        List<Map<String, Object>> blocks = List.of(
            Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", body))
        );
        slack.postMessage(slackUserId, "Orbit · Snapshot " + result.id(), blocks);
        return link;
    }

    private Map<String, Object> buildView(List<Portfolio> active, AppUser user) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(staticSelect("portfolio_block", "portfolio", "Portfolio",
            active.stream().map(p -> option(String.valueOf(p.getId()), p.getName())).toList(),
            null));
        blocks.add(staticSelect("lens_block", "lens", "Lens (role)",
            LENSES.stream().map(l -> option(l, prettyLens(l))).toList(),
            user.getRole() == null ? null : option(user.getRole(), prettyLens(user.getRole()))));
        // Project select left empty for Phase C: the frontend page already
        // honours a missing project (renders portfolio-wide). A future revision
        // can populate this from PortfolioRepository's project join.
        blocks.add(Map.of(
            "type", "input",
            "block_id", "project_block",
            "optional", true,
            "element", Map.of(
                "type", "plain_text_input",
                "action_id", "project",
                "placeholder", Map.of("type", "plain_text", "text", "Project ID (optional)")
            ),
            "label", Map.of("type", "plain_text", "text", "Project (optional)")
        ));

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("type", "modal");
        view.put("callback_id", CALLBACK_ID);
        view.put("title",  Map.of("type", "plain_text", "text", "Orbit snapshot"));
        view.put("submit", Map.of("type", "plain_text", "text", "Generate"));
        view.put("close",  Map.of("type", "plain_text", "text", "Cancel"));
        view.put("blocks", blocks);
        return view;
    }

    private static Map<String, Object> staticSelect(String blockId, String actionId, String label,
                                                    List<Map<String, Object>> options,
                                                    Map<String, Object> initial) {
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("type", "static_select");
        element.put("action_id", actionId);
        element.put("placeholder", Map.of("type", "plain_text", "text", "Select " + label.toLowerCase()));
        element.put("options", options);
        if (initial != null) element.put("initial_option", initial);

        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "input");
        block.put("block_id", blockId);
        block.put("element", element);
        block.put("label", Map.of("type", "plain_text", "text", label));
        return block;
    }

    private static Map<String, Object> option(String value, String label) {
        return Map.of(
            "text", Map.of("type", "plain_text", "text", label),
            "value", value
        );
    }

    private static String prettyLens(String l) {
        return switch (l == null ? "" : l) {
            case "LEADERSHIP"  -> "Leadership";
            case "ENGINEERING" -> "Engineering";
            case "PM"          -> "Project Management";
            case "CSM"         -> "Account Mgmt / CSM";
            case "REVENUE"     -> "Revenue";
            default            -> l;
        };
    }

    private static Long selectedLong(JsonNode values, String blockId, String actionId) {
        String s = selectedString(values, blockId, actionId);
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private static String selectedString(JsonNode values, String blockId, String actionId) {
        JsonNode node = values.path(blockId).path(actionId);
        // static_select → selected_option.value; plain_text_input → value
        JsonNode selected = node.path("selected_option").path("value");
        if (!selected.isMissingNode() && !selected.isNull()) return selected.asText();
        JsonNode plain = node.path("value");
        if (!plain.isMissingNode() && !plain.isNull()) return plain.asText();
        return null;
    }
}
