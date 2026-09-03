package com.orbit.integration.jira;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Parses Jira REST timestamps (e.g. {@code 2026-07-14T10:32:00.000+0530}) into local time. */
public final class JiraDates {

    // Jira emits a zone offset without a colon, which ISO_OFFSET_DATE_TIME rejects
    private static final DateTimeFormatter JIRA_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private JiraDates() {}

    public static LocalDateTime parse(Object value) {
        if (!(value instanceof String s) || s.isBlank()) return null;
        try {
            return toLocal(OffsetDateTime.parse(s, JIRA_FORMAT));
        } catch (DateTimeParseException e) {
            try {
                return toLocal(OffsetDateTime.parse(s));
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static LocalDateTime toLocal(OffsetDateTime odt) {
        return odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
