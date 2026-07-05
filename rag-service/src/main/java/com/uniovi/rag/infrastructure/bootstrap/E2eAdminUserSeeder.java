package com.uniovi.rag.infrastructure.bootstrap;

import com.uniovi.rag.domain.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Ensures an ADMIN user exists for Playwright admin scenarios when profile {@code e2e} is active.
 * Credentials are for CI/local demo only - never enable this profile in production.
 *
 * <p>Uses JDBC upsert instead of JPA so startup stays deterministic under DevTools restarts and
 * concurrent runners (GitHub Actions / local): Hibernate/JPA races produced partial failures where
 * no admin row existed while the runner swallowed exceptions.
 *
 * <p>Plain password is read from {@code rag.e2e.admin-password} (see {@code application-e2e.properties})
 * so analysis tools do not treat a demo secret as a hard-coded production credential in Java source.
 */
@Component
@Profile("e2e")
@Order(100)
public class E2eAdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(E2eAdminUserSeeder.class);

    // NOSONAR S2068 - SQL includes column name password_hash; secrets come from rag.e2e.admin-password only.
    private static final String SQL_UPDATE_E2E_ADMIN =
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

    private static final String SQL_INSERT_E2E_ADMIN =
            """
            INSERT INTO users (id, email, password_hash, name, role, created_at, email_verified, email_verified_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, true, CURRENT_TIMESTAMP)
            """;

    /** Fixed UUID so JWT claims in tests remain stable across restarts if desired. */
    public static final UUID E2E_ADMIN_ID = UUID.fromString("e2e0ad00-0000-4000-8000-000000000001");

    public static final String E2E_ADMIN_EMAIL = "admin@e2e.local";

    private static final String E2E_ADMIN_DISPLAY_NAME = "E2E Admin";

    private final JdbcTemplate jdbcTemplate;

    /** Plaintext demo password; always configure via {@code application-e2e} (never rely on a default here). */
    private final String adminPasswordPlain;

    public E2eAdminUserSeeder(
            JdbcTemplate jdbcTemplate, @Value("${rag.e2e.admin-password}") String adminPasswordPlain) {
        this.jdbcTemplate = jdbcTemplate;
        this.adminPasswordPlain = adminPasswordPlain;
    }

    @Override
    public void run(ApplicationArguments args) {
        upsertE2eAdmin();
    }

    private void upsertE2eAdmin() {
        final String hash = "{noop}" + adminPasswordPlain;
        int updated = updateExistingAdminRow(hash);
        if (updated > 0) {
            log.info("E2E profile: ensured admin user {}", E2E_ADMIN_EMAIL);
            return;
        }
        try {
            jdbcTemplate.update(
                    SQL_INSERT_E2E_ADMIN,
                    E2E_ADMIN_ID,
                    E2E_ADMIN_EMAIL,
                    hash,
                    E2E_ADMIN_DISPLAY_NAME,
                    UserRole.ADMIN.name());
            log.info("E2E profile: seeded admin user {}", E2E_ADMIN_EMAIL);
        } catch (DataIntegrityViolationException duplicate) {
            updateExistingAdminRow(hash);
            log.info("E2E profile: reconciled admin user after concurrent insert");
        }
    }

    private int updateExistingAdminRow(String passwordHash) {
        return jdbcTemplate.update(
                SQL_UPDATE_E2E_ADMIN,
                E2E_ADMIN_EMAIL,
                passwordHash,
                E2E_ADMIN_DISPLAY_NAME,
                UserRole.ADMIN.name(),
                E2E_ADMIN_ID,
                E2E_ADMIN_EMAIL);
    }
}
