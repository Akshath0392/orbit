package com.orbit.connector.hrms.darwinbox;

import com.orbit.connector.hrms.HrmsSettingField;
import com.orbit.connector.hrms.HrmsSettings;
import com.orbit.repository.AppUserRepository;
import com.orbit.repository.AttendanceRecordRepository;
import com.orbit.repository.LeaveBalanceRepository;
import com.orbit.repository.LeaveRecordRepository;
import com.orbit.repository.WfhRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DarwinboxHrmsConnectorTest {

    @Mock LeaveRecordRepository      leaves;
    @Mock LeaveBalanceRepository     balances;
    @Mock AttendanceRecordRepository attendance;
    @Mock AppUserRepository          users;
    @Mock WfhRecordRepository        wfhRecords;

    private DarwinboxHrmsConnector connector() {
        return new DarwinboxHrmsConnector(leaves, balances, attendance, users, wfhRecords);
    }

    private HrmsSettings settings(Map<String, Object> values) { return new HrmsSettings(values); }

    // ── Provider identity ─────────────────────────────────────────────────────

    @Test
    void providerIdentity() {
        var c = connector();
        assertThat(c.providerKey()).isEqualTo("darwinbox");
        assertThat(c.displayName()).isEqualTo("Darwinbox");
        assertThat(c.webhookSignatureHeader()).isEqualTo("X-Darwin-Signature");
    }

    // ── Settings descriptor ───────────────────────────────────────────────────

    @Test
    void descriptorRequiresTenantUrlCompanyIdAndApiKey() {
        List<HrmsSettingField> fields = connector().settingsDescriptor();
        var byKey = fields.stream().collect(java.util.stream.Collectors.toMap(HrmsSettingField::key, f -> f));

        assertThat(byKey.get("baseUrl").required()).isTrue();
        assertThat(byKey.get("baseUrl").type()).isEqualTo("url");
        assertThat(byKey.get("baseUrl").placeholder()).doesNotContain("darwinbox.io");
        assertThat(byKey.get("companyId").required()).isTrue();
        assertThat(byKey.get("apiKey").required()).isTrue();
        assertThat(byKey.get("apiKey").secret()).isTrue();
        assertThat(byKey.get("webhookSecret").secret()).isTrue();
        assertThat(byKey.get("authType").options()).containsExactly("API_KEY", "BEARER", "HMAC");
    }

    // ── Configured check ──────────────────────────────────────────────────────

    @Test
    void isConfiguredNeedsAllThreeCredentials() {
        var c = connector();
        assertThat(c.isConfigured(HrmsSettings.empty())).isFalse();
        assertThat(c.isConfigured(settings(Map.of("baseUrl", "https://x.darwinbox.in")))).isFalse();
        assertThat(c.isConfigured(settings(Map.of(
            "baseUrl", "https://x.darwinbox.in", "apiKey", "k")))).isFalse();
        assertThat(c.isConfigured(settings(Map.of(
            "baseUrl", "https://x.darwinbox.in", "apiKey", "k", "companyId", "co")))).isTrue();
    }

    @Test
    void isConfiguredTreatsBlankValuesAsMissing() {
        assertThat(connector().isConfigured(settings(Map.of(
            "baseUrl", "  ", "apiKey", "k", "companyId", "co")))).isFalse();
    }

    // ── Test connection ───────────────────────────────────────────────────────

    @Test
    void testConnectionFailsFastWhenUnconfigured() {
        Map<String, Object> result = connector().testConnection(HrmsSettings.empty());
        assertThat(result.get("ok")).isEqualTo(false);
        assertThat(result.get("error").toString()).contains("required");
    }

    // ── Value mapping ─────────────────────────────────────────────────────────

    @Test
    void normaliseStatusMapsDarwinboxValuesToCanonical() {
        assertThat(DarwinboxHrmsConnector.normaliseStatus("Approved")).isEqualTo("APPROVED");
        assertThat(DarwinboxHrmsConnector.normaliseStatus("approve")).isEqualTo("APPROVED");
        assertThat(DarwinboxHrmsConnector.normaliseStatus("Rejected")).isEqualTo("REJECTED");
        assertThat(DarwinboxHrmsConnector.normaliseStatus("cancel")).isEqualTo("CANCELLED");
        assertThat(DarwinboxHrmsConnector.normaliseStatus("anything-else")).isEqualTo("PENDING");
        assertThat(DarwinboxHrmsConnector.normaliseStatus(null)).isEqualTo("PENDING");
    }

    @Test
    void mapWfhTypeNormalisesHalfDayVariants() {
        assertThat(DarwinboxHrmsConnector.mapWfhType("First Half")).isEqualTo("HALF_DAY_AM");
        assertThat(DarwinboxHrmsConnector.mapWfhType("half-day-pm")).isEqualTo("HALF_DAY_PM");
        assertThat(DarwinboxHrmsConnector.mapWfhType("full day")).isEqualTo("FULL_DAY");
        assertThat(DarwinboxHrmsConnector.mapWfhType(null)).isEqualTo("FULL_DAY");
    }

    // ── Webhook robustness ────────────────────────────────────────────────────

    @Test
    void webhookIgnoresPayloadWithoutEventType() {
        connector().processWebhookEvent(HrmsSettings.empty(), Map.of("data", Map.of()));
        org.mockito.Mockito.verifyNoInteractions(leaves, wfhRecords, users);
    }

    @Test
    void webhookIgnoresUnknownEventTypes() {
        connector().processWebhookEvent(HrmsSettings.empty(), Map.of("event_type", "payroll_run"));
        org.mockito.Mockito.verifyNoInteractions(leaves, wfhRecords, users);
    }
}
