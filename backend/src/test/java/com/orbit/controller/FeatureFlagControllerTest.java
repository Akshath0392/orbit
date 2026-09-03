package com.orbit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.config.FeatureFlag;
import com.orbit.repository.FeatureFlagRepository;
import com.orbit.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = FeatureFlagController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class FeatureFlagControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean FeatureFlagRepository flags;
    @MockBean JwtService jwtService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private void authAs(String email, String role) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(
            email, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static FeatureFlag flag(String key, String audience, List<String> pilots) {
        FeatureFlag f = new FeatureFlag();
        f.setFlagKey(key);
        f.setAudience(audience);
        f.setPilotEmails(pilots);
        return f;
    }

    @Test
    void effectiveResolvesAllNoneAndPilotForRegularUser() throws Exception {
        when(flags.findAll()).thenReturn(List.of(
            flag("screen.uat", "ALL", List.of()),
            flag("screen.capacity", "NONE", List.of()),
            flag("screen.reports", "PILOT", List.of("pilot@orbit.io"))
        ));
        authAs("pilot@orbit.io", "PM");

        mvc.perform(get("/api/v1/feature-flags/effective"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$['screen.uat']").value(true))
            .andExpect(jsonPath("$['screen.capacity']").value(false))
            .andExpect(jsonPath("$['screen.reports']").value(true));
    }

    @Test
    void effectiveHidesPilotFlagFromNonPilotUser() throws Exception {
        when(flags.findAll()).thenReturn(List.of(
            flag("screen.reports", "PILOT", List.of("pilot@orbit.io"))
        ));
        authAs("someone-else@orbit.io", "PM");

        mvc.perform(get("/api/v1/feature-flags/effective"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$['screen.reports']").value(false));
    }

    @Test
    void adminSeesEverythingRegardlessOfAudience() throws Exception {
        when(flags.findAll()).thenReturn(List.of(
            flag("screen.capacity", "NONE", List.of()),
            flag("screen.reports", "PILOT", List.of("pilot@orbit.io"))
        ));
        authAs("admin@orbit.io", "ADMIN");

        mvc.perform(get("/api/v1/feature-flags/effective"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$['screen.capacity']").value(true))
            .andExpect(jsonPath("$['screen.reports']").value(true));
    }

    @Test
    void pilotEmailMatchIsCaseInsensitive() throws Exception {
        when(flags.findAll()).thenReturn(List.of(
            flag("screen.reports", "PILOT", List.of("Pilot@Orbit.io"))
        ));
        authAs("pilot@orbit.io", "PM");

        mvc.perform(get("/api/v1/feature-flags/effective"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$['screen.reports']").value(true));
    }

    @Test
    void adminListIsPaginated() throws Exception {
        when(flags.findAll(any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(
                List.of(flag("screen.uat", "NONE", List.of()))));
        authAs("admin@orbit.io", "ADMIN");

        mvc.perform(get("/api/v1/admin/feature-flags").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].flagKey").value("screen.uat"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void upsertCreatesFlagAndNormalisesEmails() throws Exception {
        when(flags.findByFlagKey("screen.uat")).thenReturn(Optional.empty());
        when(flags.save(any(FeatureFlag.class))).thenAnswer(inv -> inv.getArgument(0));
        authAs("admin@orbit.io", "ADMIN");

        mvc.perform(post("/api/v1/admin/feature-flags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "flagKey", "screen.uat",
                    "audience", "pilot",
                    "pilotEmails", List.of(" A@x.io ", "a@x.io", "b@x.io")
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flagKey").value("screen.uat"))
            .andExpect(jsonPath("$.audience").value("PILOT"))
            .andExpect(jsonPath("$.pilotEmails.length()").value(2))
            .andExpect(jsonPath("$.updatedBy").value("admin@orbit.io"));

        verify(flags).save(any(FeatureFlag.class));
    }

    @Test
    void upsertRejectsMissingKeyAndBadAudience() throws Exception {
        mvc.perform(post("/api/v1/admin/feature-flags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/admin/feature-flags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("flagKey", "x", "audience", "SOMETIMES"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRemovesExistingFlag() throws Exception {
        when(flags.existsById(7L)).thenReturn(true);
        mvc.perform(delete("/api/v1/admin/feature-flags/7"))
            .andExpect(status().isNoContent());
        verify(flags).deleteById(7L);
    }

    @Test
    void deleteReturns404ForUnknownFlag() throws Exception {
        when(flags.existsById(99L)).thenReturn(false);
        mvc.perform(delete("/api/v1/admin/feature-flags/99"))
            .andExpect(status().isNotFound());
    }
}
