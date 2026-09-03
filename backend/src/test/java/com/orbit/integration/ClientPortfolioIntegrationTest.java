package com.orbit.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Regression: Client and Portfolio CRUD against the real orbit DB.
 *
 * NOTE: @Transactional is intentionally absent. With @SpringBootTest + MockMvc,
 * each mvc.perform() runs inside its own servlet-layer transaction that is
 * committed immediately — the test's outer transaction never wraps it.
 * Cleanup is done explicitly in @BeforeEach instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientPortfolioIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private String token;

    // Codes / names / emails used across all tests in this class
    private static final String[] TEST_CODES   = {"TC","ALP","ON","PC","FC","TD"};
    private static final String[] TEST_PORTF   = {"Lending","Insurance"};
    private static final String   TEST_USER     = "newpjm@orbit.io";

    @BeforeEach
    void setUp() throws Exception {
        // Hard-delete any rows left by a previous run.
        // (MockMvc calls commit to the DB; @Transactional on the test class has no effect on them.)
        jdbc.update("DELETE FROM portfolios WHERE name IN ('Lending','Insurance')");
        jdbc.update("DELETE FROM projects  WHERE client_id IN "
                  + "(SELECT id FROM clients WHERE code IN ('TC','ALP','ON','PC','FC','TD'))");
        jdbc.update("DELETE FROM clients   WHERE code IN ('TC','ALP','ON','PC','FC','TD')");
        jdbc.update("DELETE FROM app_users WHERE email = 'newpjm@orbit.io'");

        MvcResult r = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    Map.of("email", "admin@orbit.io", "password", "gauge123"))))
            .andReturn();
        token = "Bearer " + mapper.readTree(r.getResponse().getContentAsString())
            .get("token").asText();
    }

    // ── Client CRUD ───────────────────────────────────────────────────────────

    @Test
    void createClientAppearsInList() throws Exception {
        mvc.perform(post("/api/v1/admin/clients")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "name", "Test Corp", "code", "TC",
                    "contactName", "Jane Smith",
                    "healthGreenThreshold", 80,
                    "healthAmberThreshold", 60
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber());

        mvc.perform(get("/api/v1/clients").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name=='Test Corp')]").isNotEmpty());
    }

    @Test
    void createdClientHasCorrectFields() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/clients")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "name", "Alpha Corp", "code", "ALP",
                    "healthGreenThreshold", 75, "healthAmberThreshold", 55
                ))))
            .andExpect(status().isOk())
            .andReturn();

        long id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/v1/clients").header("Authorization", token))
            .andExpect(jsonPath("$[?(@.id==" + id + ")].code").value("ALP"))
            .andExpect(jsonPath("$[?(@.id==" + id + ")].healthGreenThreshold").value(75));
    }

    @Test
    void updateClientChangesFields() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/admin/clients")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Old Name", "code", "ON"))))
            .andReturn();
        long id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(put("/api/v1/admin/clients/" + id)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "New Name"))))
            .andExpect(status().isOk());

        mvc.perform(get("/api/v1/clients").header("Authorization", token))
            .andExpect(jsonPath("$[?(@.id==" + id + ")].name").value("New Name"));
    }

    @Test
    void deactivateClientRemovesFromList() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/admin/clients")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "ToDelete", "code", "TD"))))
            .andReturn();
        long id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(delete("/api/v1/admin/clients/" + id)
                .header("Authorization", token))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/clients").header("Authorization", token))
            .andExpect(jsonPath("$[?(@.id==" + id + ")]").isEmpty());
    }

    // ── Portfolio CRUD ────────────────────────────────────────────────────────

    @Test
    void createPortfolioUnderClientAppearsInList() throws Exception {
        MvcResult clientResult = mvc.perform(post("/api/v1/admin/clients")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Portfolio Corp", "code", "PC"))))
            .andReturn();
        long clientId = mapper.readTree(clientResult.getResponse().getContentAsString())
            .get("id").asLong();

        mvc.perform(post("/api/v1/portfolios")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "name", "Lending", "clientId", clientId, "description", "Lending products"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value("Lending"))
            .andExpect(jsonPath("$.clientName").value("Portfolio Corp"));
    }

    @Test
    void portfolioListFiltersbyClientId() throws Exception {
        MvcResult clientResult = mvc.perform(post("/api/v1/admin/clients")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Filter Corp", "code", "FC"))))
            .andReturn();
        long clientId = mapper.readTree(clientResult.getResponse().getContentAsString())
            .get("id").asLong();

        mvc.perform(post("/api/v1/portfolios")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Insurance", "clientId", clientId))))
            .andExpect(status().isOk());

        mvc.perform(get("/api/v1/portfolios").param("clientId", String.valueOf(clientId))
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Insurance"));
    }

    // ── Bulk user import ──────────────────────────────────────────────────────

    @Test
    void bulkImportCreatesAndSkipsCorrectly() throws Exception {
        JsonNode result = mapper.readTree(
            mvc.perform(post("/api/v1/admin/users/bulk")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(java.util.List.of(
                        Map.of("name", "New PJM", "email", "newpjm@orbit.io", "role", "PM"),
                        Map.of("name", "Admin Dup", "email", "admin@orbit.io", "role", "ADMIN")
                    ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
        );

        assertThat(result.get("processed").asInt()).isEqualTo(2);
        // admin@orbit.io already exists → skipped
        boolean adminSkipped = false;
        boolean newCreated = false;
        for (JsonNode r : result.get("results")) {
            if ("admin@orbit.io".equals(r.get("email").asText()))
                adminSkipped = "skipped_exists".equals(r.get("status").asText());
            if ("newpjm@orbit.io".equals(r.get("email").asText()))
                newCreated = "created".equals(r.get("status").asText());
        }
        assertThat(adminSkipped).isTrue();
        assertThat(newCreated).isTrue();
    }
}
