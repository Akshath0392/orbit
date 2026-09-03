package com.orbit.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Regression: full auth flow against the real orbit DB.
 * Verifies login → token → access protected endpoints works end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    void adminCanLoginAndReceiveToken() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    Map.of("email", "admin@orbit.io", "password", "gauge123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.user.role").value("ADMIN"))
            .andReturn();

        String token = mapper.readTree(result.getResponse().getContentAsString())
            .get("token").asText();
        assertThat(token).isNotBlank();
    }

    @Test
    void invalidPasswordReturns401() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    Map.of("email", "admin@orbit.io", "password", "wrongpass"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void unknownEmailReturns401() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    Map.of("email", "ghost@orbit.io", "password", "any"))))
            .andExpect(status().isUnauthorized());
    }

    // ── Token usage ───────────────────────────────────────────────────────────

    @Test
    void tokenAllowsAccessToProtectedEndpoint() throws Exception {
        String token = loginAndGetToken("admin@orbit.io", "gauge123");

        mvc.perform(get("/api/v1/clients")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/clients"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithGarbageTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/clients")
                .header("Authorization", "Bearer not.a.valid.jwt"))
            .andExpect(status().isUnauthorized());
    }

    // ── RBAC ─────────────────────────────────────────────────────────────────

    @Test
    void adminCanAccessAdminEndpoints() throws Exception {
        String token = loginAndGetToken("admin@orbit.io", "gauge123");

        mvc.perform(get("/api/v1/admin/users")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanAccessRoles() throws Exception {
        String token = loginAndGetToken("admin@orbit.io", "gauge123");

        mvc.perform(get("/api/v1/admin/roles")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn();
        return mapper.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }
}
