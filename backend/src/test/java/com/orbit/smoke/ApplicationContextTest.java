package com.orbit.smoke;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sanity: verifies the Spring context loads without errors.
 * Fails fast on wiring/config mistakes before any other test runs.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Test
    void contextLoads() {
        // passes if Spring context starts without exception
    }
}
