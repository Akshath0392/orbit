package com.orbit.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke: every protected endpoint returns 401 (not 404/500) when unauthenticated,
 * and auth endpoint returns 200 with valid credentials.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AllEndpointsSmokeTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String token;

    @BeforeEach
    void login() throws Exception {
        String body = mapper.writeValueAsString(
            Map.of("email", "admin@orbit.io", "password", "gauge123"));
        String resp = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        token = "Bearer " + mapper.readTree(resp).get("token").asText();
    }

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        for (String path : new String[]{
            "/api/v1/dashboard/radar",
            "/api/v1/clients",
            "/api/v1/alerts",
            "/api/v1/man-days",
            "/api/v1/projects",
            "/api/v1/portfolios",
            "/api/v1/hrms/runs",
            "/api/v1/admin/roles",
            "/api/v1/admin/users",
        }) {
            mvc.perform(get(path))
               .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void authenticatedRequestsReturn2xx() throws Exception {
        for (String path : new String[]{
            "/api/v1/clients",
            "/api/v1/projects",
            "/api/v1/portfolios",
            "/api/v1/alerts",
            "/api/v1/man-days",
            "/api/v1/admin/roles",
        }) {
            mvc.perform(get(path).header("Authorization", token))
               .andExpect(status().is2xxSuccessful());
        }
    }

    @Test
    void swaggerUiIsAccessible() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
           .andExpect(status().is3xxRedirection());
    }
}
