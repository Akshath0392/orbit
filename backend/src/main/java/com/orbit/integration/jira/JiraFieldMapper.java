package com.orbit.integration.jira;

import com.orbit.domain.config.JiraConfig;
import com.orbit.domain.issue.JiraIssue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies the optional jira_config custom-field mappings (story points,
 * Sprint, Solutioning Manager, PjM — V85; Developer — V98) plus the standard
 * reporter object to an issue. Shared by the JQL sync and the webhook so both
 * paths parse identically. Unmapped or malformed values leave the issue
 * untouched — blank mapping means dark feature, never wrong data.
 */
public final class JiraFieldMapper {

    private JiraFieldMapper() {}

    // Legacy/company-managed Sprint field renders as strings like
    // "com.atlassian.greenhopper.service.sprint.Sprint@...[id=42,state=ACTIVE,name=S28,...]"
    // — attribute order varies, so each is extracted independently.
    private static final Pattern SPRINT_ID = Pattern.compile("[\\[,]id=(\\d+)");
    private static final Pattern SPRINT_NAME = Pattern.compile("[\\[,]name=([^,\\]]+)");
    private static final Pattern SPRINT_STATE = Pattern.compile("[\\[,]state=([^,\\]]+)");

    public static void apply(JiraIssue issue, Map<String, Object> fields, JiraConfig cfg) {
        if (fields == null || cfg == null) return;

        if (notBlank(cfg.getStoryPointsField())) {
            Object sp = fields.get(cfg.getStoryPointsField());
            if (sp instanceof Number n) issue.setStoryPoints(BigDecimal.valueOf(n.doubleValue()));
        }
        if (notBlank(cfg.getSmField())) {
            String v = personName(fields.get(cfg.getSmField()));
            if (v != null) issue.setSmOwner(v);
        }
        if (notBlank(cfg.getPjmField())) {
            String v = personName(fields.get(cfg.getPjmField()));
            if (v != null) issue.setPjmOwner(v);
        }
        if (notBlank(cfg.getDeveloperField())) {
            String v = personName(fields.get(cfg.getDeveloperField()));
            if (v != null) issue.setDeveloperName(v);
        }
        if (notBlank(cfg.getSprintField())) {
            applySprint(issue, fields.get(cfg.getSprintField()));
        }
        applyReporter(issue, fields.get("reporter"));
    }

    /**
     * Standard reporter field (V98) — who raised the issue. emailAddress can
     * be absent when the reporter hides it (Atlassian privacy setting).
     */
    @SuppressWarnings("unchecked")
    private static void applyReporter(JiraIssue issue, Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> reporter = (Map<String, Object>) m;
            issue.setReporterName((String) reporter.get("displayName"));
            issue.setReporterEmail((String) reporter.get("emailAddress"));
        }
    }

    /** User-picker fields arrive as {displayName: ...}; text fields as plain strings. */
    @SuppressWarnings("unchecked")
    private static String personName(Object v) {
        if (v instanceof Map<?, ?> m) {
            Object name = ((Map<String, Object>) m).get("displayName");
            return name instanceof String s && !s.isBlank() ? s : null;
        }
        if (v instanceof String s && !s.isBlank()) return s.trim();
        return null;
    }

    /**
     * Jira Cloud's Sprint field is an array of sprint objects (or legacy
     * toString lines). The issue's CURRENT sprint = the active one, else the
     * last entry (most recent). Full sprint modelling lands in Wave 3.
     */
    @SuppressWarnings("unchecked")
    private static void applySprint(JiraIssue issue, Object v) {
        if (!(v instanceof List<?> list) || list.isEmpty()) return;
        Long id = null;
        String name = null;
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> sprint = (Map<String, Object>) m;
                Object sid = sprint.get("id");
                Object sname = sprint.get("name");
                Object state = sprint.get("state");
                boolean active = state instanceof String st && st.equalsIgnoreCase("active");
                if (id == null || active) {
                    id = sid instanceof Number n ? n.longValue() : id;
                    name = sname instanceof String s ? s : name;
                    if (active) break;
                }
            } else if (o instanceof String s) {
                Matcher idM = SPRINT_ID.matcher(s);
                Matcher nameM = SPRINT_NAME.matcher(s);
                Matcher stateM = SPRINT_STATE.matcher(s);
                if (idM.find()) {
                    boolean active = stateM.find() && "ACTIVE".equalsIgnoreCase(stateM.group(1));
                    if (id == null || active) {
                        id = Long.parseLong(idM.group(1));
                        name = nameM.find() ? nameM.group(1) : name;
                        if (active) break;
                    }
                }
            }
        }
        if (id != null) issue.setCurrentSprintId(id);
        if (name != null) issue.setCurrentSprintName(name);
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
