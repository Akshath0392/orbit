package com.orbit.util;

/**
 * CSV cell escaping that is safe against both delimiter breakage and spreadsheet
 * formula (CSV) injection (audit M3).
 *
 * <p>A cell that begins with {@code = + - @} (or a leading tab/CR) is interpreted
 * as a formula by Excel/Google Sheets, so field content that arrives from external
 * sources (e.g. a Jira issue summary like {@code =cmd|'/C calc'!A1}) must be
 * neutralized with a leading apostrophe before writing.
 */
public final class Csv {

    private Csv() {}

    public static String escape(Object val) {
        if (val == null) return "";
        String s = val.toString();
        if (!s.isEmpty()) {
            char c0 = s.charAt(0);
            if (c0 == '=' || c0 == '+' || c0 == '-' || c0 == '@' || c0 == '\t' || c0 == '\r') {
                s = "'" + s;
            }
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
