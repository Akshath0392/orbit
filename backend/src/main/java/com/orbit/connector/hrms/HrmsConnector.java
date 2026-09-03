package com.orbit.connector.hrms;

import java.util.List;
import java.util.Map;

/**
 * Pluggable HRMS provider. Implementations register as Spring beans and are
 * collected by {@link HrmsConnectorFactory}; adding a provider (Keka,
 * BambooHR, Workday, …) is one class — no core changes.
 *
 * All operations receive the settings blob from the single hrms_config row;
 * connectors hold no credential state of their own.
 */
public interface HrmsConnector {

    String providerKey();

    String displayName();

    /** Drives the dynamic settings form in the HR System integration card. */
    List<HrmsSettingField> settingsDescriptor();

    /** True when every credential needed for live sync is present. */
    boolean isConfigured(HrmsSettings settings);

    /** Cheap live-credential probe. Returns {ok, message|error}. */
    Map<String, Object> testConnection(HrmsSettings settings);

    /**
     * Pull employees, leaves, WFH, balances and attendance into the shared
     * provider-agnostic tables. syncType is FULL or DELTA.
     *
     * @return number of records pulled
     */
    int sync(HrmsSettings settings, String syncType);

    /** Provider push event, already signature-verified by the webhook endpoint. */
    void processWebhookEvent(HrmsSettings settings, Map<String, Object> payload);

    /** Header carrying the HMAC-SHA256 webhook signature for this provider. */
    default String webhookSignatureHeader() { return "X-Hrms-Signature"; }
}
