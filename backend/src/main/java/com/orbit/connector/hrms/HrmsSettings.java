package com.orbit.connector.hrms;

import java.util.Map;

/** Typed view over the provider settings JSONB blob stored in hrms_config. */
public record HrmsSettings(Map<String, Object> values) {

    public static HrmsSettings empty() { return new HrmsSettings(Map.of()); }

    public String string(String key) {
        Object v = values.get(key);
        if (v == null) return null;
        String s = v.toString().strip();
        return s.isEmpty() ? null : s;
    }

    public String string(String key, String fallback) {
        String s = string(key);
        return s != null ? s : fallback;
    }

    public int integer(String key, int fallback) {
        Object v = values.get(key);
        if (v instanceof Number n) return n.intValue();
        try { return (int) Double.parseDouble(v.toString()); }
        catch (Exception e) { return fallback; }
    }

    public boolean has(String key) { return string(key) != null; }
}
