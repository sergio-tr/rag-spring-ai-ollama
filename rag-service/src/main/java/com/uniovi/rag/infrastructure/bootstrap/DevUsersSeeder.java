package com.uniovi.rag.infrastructure.bootstrap;

import com.uniovi.rag.domain.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Seeds a minimal set of users for local development only (profile {@code dev}).
 *
 * <p>Creates (or updates) an ADMIN and a regular USER account so the UI can be exercised without
 * manual DB edits. Credentials are configurable via environment variables (see {@code rag-service/.env.example}).
 *
 * <p>Never enable this in production.
 */
@Component
@Profile("dev")
@Order(50)
public class DevUsersSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUsersSeeder.class);

    private static final String SQL_UPDATE_USER =
            """
            UPDATE users SET
              email = ?,
              password_hash = ?,
              name = ?,
              role = ?,
              email_verified = true,
              email_verified_at = CURRENT_TIMESTAMP
            WHERE id = ? OR lower(email) = lower(?)
            """;

    private static final String SQL_INSERT_USER =
            """
            INSERT INTO users (id, email, password_hash, name, role, created_at, email_verified, email_verified_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, true, CURRENT_TIMESTAMP)
            """;

    public static final UUID DEV_ADMIN_ID = UUID.fromString("deva11d0-0000-4000-8000-000000000001");
    public static final UUID DEV_USER_ID = UUID.fromString("deu50000-0000-4000-8000-000000000001");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    private final String adminEmail;
    private final String adminPasswordPlain;
    private final String adminDisplayName;

    private final String userEmail;
    private final String userPasswordPlain;
    private final String userDisplayName;

    public DevUsersSeeder(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${rag.dev.seed.admin.email:admin@dev.local}") String adminEmail,
            @Value("${rag.dev.seed.admin.password:dev}") String adminPasswordPlain,
            @Value("${rag.dev.seed.admin.name:Dev Admin}") String adminDisplayName,
            @Value("${rag.dev.seed.user.email:user@dev.local}") String userEmail,
            @Value("${rag.dev.seed.user.password:dev}") String userPasswordPlain,
            @Value("${rag.dev.seed.user.name:Dev User}") String userDisplayName) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPasswordPlain = adminPasswordPlain;
        this.adminDisplayName = adminDisplayName;
        this.userEmail = userEmail;
        this.userPasswordPlain = userPasswordPlain;
        this.userDisplayName = userDisplayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        upsertUser(
                DEV_ADMIN_ID, adminEmail, adminPasswordPlain, adminDisplayName, UserRole.ADMIN);
        upsertUser(
                DEV_USER_ID, userEmail, userPasswordPlain, userDisplayName, UserRole.USER);
    }

    private void upsertUser(UUID id, String email, String passwordPlain, String displayName, UserRole role) {
        String normalizedEmail = (email == null ? "" : email.trim().toLowerCase());
        String hash = passwordEncoder.encode(passwordPlain == null ? "" : passwordPlain);

        int updated =
                jdbcTemplate.update(
                        SQL_UPDATE_USER,
                        normalizedEmail,
                        hash,
                        displayName,
                        role.name(),
                        id,
                        normalizedEmail);
        if (updated > 0) {
            log.info("Dev profile: ensured {} user {}", role.name(), normalizedEmail);
            return;
        }

        jdbcTemplate.update(
                SQL_INSERT_USER,
                id,
                normalizedEmail,
                hash,
                displayName,
                role.name());
        log.info("Dev profile: seeded {} user {}", role.name(), normalizedEmail);
    }
}

