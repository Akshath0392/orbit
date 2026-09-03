package com.orbit.connector.hrms;

import java.util.List;

/**
 * One entry in a connector's settings descriptor. The frontend renders the
 * provider settings form directly from this list, so adding a provider needs
 * no UI changes.
 *
 * type: text | url | password | select | number
 * secret: value is write-only — the API never echoes it back, only a set/unset flag.
 */
public record HrmsSettingField(
        String key,
        String label,
        String type,
        boolean required,
        boolean secret,
        String placeholder,
        List<String> options) {

    public static HrmsSettingField text(String key, String label, boolean required, String placeholder) {
        return new HrmsSettingField(key, label, "text", required, false, placeholder, List.of());
    }

    public static HrmsSettingField url(String key, String label, boolean required, String placeholder) {
        return new HrmsSettingField(key, label, "url", required, false, placeholder, List.of());
    }

    public static HrmsSettingField secret(String key, String label, boolean required, String placeholder) {
        return new HrmsSettingField(key, label, "password", required, true, placeholder, List.of());
    }

    public static HrmsSettingField select(String key, String label, List<String> options) {
        return new HrmsSettingField(key, label, "select", false, false, null, options);
    }

    public static HrmsSettingField number(String key, String label, String placeholder) {
        return new HrmsSettingField(key, label, "number", false, false, placeholder, List.of());
    }
}
