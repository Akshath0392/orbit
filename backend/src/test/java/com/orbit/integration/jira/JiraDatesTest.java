package com.orbit.integration.jira;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class JiraDatesTest {

    @Test
    void parsesJiraRestFormatWithColonlessOffset() {
        LocalDateTime parsed = JiraDates.parse("2026-07-14T10:32:00.000+0530");
        LocalDateTime expected = OffsetDateTime.parse("2026-07-14T10:32:00.000+05:30")
            .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        assertEquals(expected, parsed);
    }

    @Test
    void parsesIsoOffsetFormat() {
        LocalDateTime parsed = JiraDates.parse("2026-07-14T10:32:00.000+05:30");
        assertNotNull(parsed);
    }

    @Test
    void parsesUtcZuluFormat() {
        LocalDateTime parsed = JiraDates.parse("2026-07-14T05:02:00Z");
        LocalDateTime expected = OffsetDateTime.parse("2026-07-14T05:02:00Z")
            .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        assertEquals(expected, parsed);
    }

    @Test
    void returnsNullForNullBlankNonStringAndGarbage() {
        assertNull(JiraDates.parse(null));
        assertNull(JiraDates.parse(""));
        assertNull(JiraDates.parse("  "));
        assertNull(JiraDates.parse(12345));
        assertNull(JiraDates.parse("not-a-date"));
    }
}
