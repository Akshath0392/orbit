package com.orbit.unit;

import com.orbit.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwt;

    @BeforeEach
    void setUp() throws Exception {
        jwt = new JwtService(new MockEnvironment());
        ReflectionTestUtils.setField(jwt, "accessExpiry", 3_600_000L);
        jwt.initKeys(); // generates an RSA key pair (no env vars in tests)
    }

    @Test
    void generateTokenContainsCorrectClaims() {
        String token = jwt.generate(42L, "priya@orbit.io", "PM", false);
        assertThat(jwt.getEmail(token)).isEqualTo("priya@orbit.io");
        assertThat(jwt.getRole(token)).isEqualTo("PM");
        assertThat(jwt.getUserId(token)).isEqualTo(42L);
    }

    @Test
    void validTokenIsValid() {
        String token = jwt.generate(1L, "admin@orbit.io", "ADMIN", false);
        assertThat(jwt.isValid(token)).isTrue();
    }

    @Test
    void tamperedTokenIsInvalid() {
        String token = jwt.generate(1L, "admin@orbit.io", "ADMIN", false);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwt.isValid(tampered)).isFalse();
    }

    @Test
    void expiredTokenIsInvalid() throws Exception {
        JwtService expiredJwt = new JwtService(new MockEnvironment());
        ReflectionTestUtils.setField(expiredJwt, "accessExpiry", -1L);
        expiredJwt.initKeys();
        String token = expiredJwt.generate(1L, "admin@orbit.io", "ADMIN", false);
        assertThat(expiredJwt.isValid(token)).isFalse();
    }

    @Test
    void differentRolesProduceDifferentTokens() {
        String pm    = jwt.generate(1L, "user@orbit.io", "PM",    false);
        String admin = jwt.generate(1L, "user@orbit.io", "ADMIN", true);
        assertThat(pm).isNotEqualTo(admin);
        assertThat(jwt.getRole(pm)).isEqualTo("PM");
        assertThat(jwt.getRole(admin)).isEqualTo("ADMIN");
    }

    @Test
    void canEditBudgetClaimRoundTrips() {
        String withPerm    = jwt.generate(5L, "pm@orbit.io", "PM", true);
        String withoutPerm = jwt.generate(6L, "pm2@orbit.io", "PM", false);
        assertThat(jwt.canEditBudget(withPerm)).isTrue();
        assertThat(jwt.canEditBudget(withoutPerm)).isFalse();
    }

    @Test
    void adminTokenCanEditBudgetByDefault() {
        String token = jwt.generate(1L, "admin@orbit.io", "ADMIN", true);
        assertThat(jwt.canEditBudget(token)).isTrue();
        assertThat(jwt.getRole(token)).isEqualTo("ADMIN");
    }
}
