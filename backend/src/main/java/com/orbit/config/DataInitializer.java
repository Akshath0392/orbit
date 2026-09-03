package com.orbit.config;

import com.orbit.domain.client.AppUser;
import com.orbit.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Seeds the bootstrap ADMIN account on an empty database.
 *
 * <p>Security: no password is hardcoded. The initial password is taken from
 * {@code orbit.admin.seed-password} (env {@code ORBIT_ADMIN_PASSWORD}). When that
 * is blank a cryptographically-random password is generated and logged ONCE at
 * WARN so the operator can retrieve it from the boot log and change it on first
 * login. This ensures no deployment ever ships with a known/published credential.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    @Value("${orbit.admin.seed-email:admin@orbit.io}")
    private String seedEmail;

    @Value("${orbit.admin.seed-password:}")
    private String seedPassword;

    public DataInitializer(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users; this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;

        String password = seedPassword;
        boolean generated = false;
        if (password == null || password.isBlank()) {
            password = randomPassword();
            generated = true;
        }

        AppUser admin = new AppUser();
        admin.setName("Admin");
        admin.setEmail(seedEmail);
        admin.setPassword(encoder.encode(password));
        admin.setRole("ADMIN");
        admin.setInitials("AD");
        admin.setAvatarColor("#6366F1");
        users.save(admin);

        if (generated) {
            log.warn("""

                ============================================================
                 SECURITY: Seeded bootstrap admin '{}' with a RANDOM password.
                 Initial password (shown once): {}
                 Log in and change it immediately. To set a fixed initial
                 password, provide ORBIT_ADMIN_PASSWORD before first boot.
                ============================================================""",
                seedEmail, password);
        } else {
            log.info("Seeded bootstrap admin '{}' from ORBIT_ADMIN_PASSWORD.", seedEmail);
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
