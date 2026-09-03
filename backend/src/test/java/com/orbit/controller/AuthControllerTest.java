package com.orbit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.domain.client.AppUser;
import com.orbit.repository.AppUserRepository;
import com.orbit.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for authentication.
 * Security filters disabled — auth logic is tested, not filter chain.
 */
@WebMvcTest(
    value = AuthController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean AppUserRepository users;
    @MockBean JwtService jwt;
    @MockBean PasswordEncoder encoder;

    @Test
    void loginWithValidCredentialsReturns200WithToken() throws Exception {
        AppUser user = buildUser("admin@orbit.io", "ADMIN", "hashed");
        when(users.findByEmail("admin@orbit.io")).thenReturn(Optional.of(user));
        when(encoder.matches("gauge123", "hashed")).thenReturn(true);
        when(jwt.generate(anyLong(), anyString(), anyString(), anyBoolean())).thenReturn("tok.en.value");

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", "admin@orbit.io", "password", "gauge123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("tok.en.value"))
            .andExpect(jsonPath("$.user.email").value("admin@orbit.io"))
            .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        AppUser user = buildUser("admin@orbit.io", "ADMIN", "hashed");
        when(users.findByEmail("admin@orbit.io")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "hashed")).thenReturn(false);

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", "admin@orbit.io", "password", "wrong"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void loginWithUnknownEmailReturns401() throws Exception {
        when(users.findByEmail("nobody@orbit.io")).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", "nobody@orbit.io", "password", "any"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginResponseContainsUserDetails() throws Exception {
        AppUser user = buildUser("priya@orbit.io", "PM", "hashed");
        user.setName("Priya K");
        user.setInitials("PK");
        when(users.findByEmail("priya@orbit.io")).thenReturn(Optional.of(user));
        when(encoder.matches("pass", "hashed")).thenReturn(true);
        when(jwt.generate(anyLong(), anyString(), anyString(), anyBoolean())).thenReturn("jwt.token");

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", "priya@orbit.io", "password", "pass"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.name").value("Priya K"))
            .andExpect(jsonPath("$.user.initials").value("PK"));
    }

    private AppUser buildUser(String email, String role, String password) {
        AppUser u = new AppUser();
        u.setEmail(email); u.setRole(role); u.setPassword(password);
        u.setName("Test User"); u.setInitials("TU"); u.setAvatarColor("#6366F1");
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }
}
