package com.orbit.service.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks in the Jira issue-type → Orbit issue-type mapping contract.
 *
 * Business rule (set by the team): a plain Jira "Bug" should land in UAT,
 * not Production. Only explicit "Production bug" types are tagged as PROD_BUG.
 */
class JiraSyncServiceMapIssueTypeTest {

    // The mapping logic is pure — no dependencies needed for this slice.
    // Spring init not required, so passing null fields is safe.
    private final JiraSyncService svc = new JiraSyncService(null, null, null, null, null, null, null, null, null);

    @Test
    void plainBugDefaultsToUatBug() {
        assertEquals("UAT_BUG", svc.mapIssueType("Bug"));
        assertEquals("UAT_BUG", svc.mapIssueType("bug"));
    }

    @Test
    void uatBugMapsToUatBug() {
        assertEquals("UAT_BUG", svc.mapIssueType("UAT Bug"));
        assertEquals("UAT_BUG", svc.mapIssueType("uat bug"));
    }

    @Test
    void uatDefectMapsToUatBug() {
        assertEquals("UAT_BUG", svc.mapIssueType("UAT Defect"));
        assertEquals("UAT_BUG", svc.mapIssueType("Defect"));
    }

    @Test
    void productionBugMapsToProdBug() {
        assertEquals("PROD_BUG", svc.mapIssueType("Production Bug"));
        assertEquals("PROD_BUG", svc.mapIssueType("production bug"));
        assertEquals("PROD_BUG", svc.mapIssueType("Prod Bug"));
        assertEquals("PROD_BUG", svc.mapIssueType("Production Defect"));
    }

    @Test
    void unknownTypeDefaultsToCr() {
        assertEquals("CR", svc.mapIssueType("Story"));
        assertEquals("CR", svc.mapIssueType("Task"));
        assertEquals("CR", svc.mapIssueType("Epic"));
        assertEquals("CR", svc.mapIssueType(""));
    }

    @Test
    void nullDefaultsToCr() {
        assertEquals("CR", svc.mapIssueType(null));
    }

    @Test
    void leadingTrailingWhitespaceIsIgnored() {
        assertEquals("UAT_BUG", svc.mapIssueType("  Bug  "));
        assertEquals("PROD_BUG", svc.mapIssueType("  Production Bug "));
    }
}
