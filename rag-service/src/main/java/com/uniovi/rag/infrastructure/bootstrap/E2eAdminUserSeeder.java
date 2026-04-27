package com.uniovi.rag.infrastructure.bootstrap;

import com.uniovi.rag.domain.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Credentials are for CI/local demo only — never enable this profile in production.
 *
 * <p>Uses JDBC upsert instead of JPA so startup stays deterministic under DevTools restarts and
 * concurrent runners (GitHub Actions / local): Hibernate/JPA races produced partial failures where
 * no admin row existed while the runner swallowed exceptions.
 */
@Component
@Profile("e2e")
@Order(100)
public class E2eAdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(E2eAdminUserSeeder.class);

    /** Fixed UUID so JWT claims in tests remain stable across restarts if desired. */
    public static final UUID E2E_ADMIN_ID = UUID.fromString("e2e0ad00-0000-4000-8000-000000000001");

    public static final String E2E_ADMIN_EMAIL = "admin@e2e.local";

    public static final String E2E_ADMIN_PASSWORD = "e2e";

    private final JdbcTemplate jdbcTemplate;

    public E2eAdminUserSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        upsertE2eAdmin();
    }

    private void upsertE2eAdmin() {
        final String hash = "{noop}" + E2E_ADMIN_PASSWORD;
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE users SET
                          email = ?,
                          password_hash = ?,
                          name = ?,
                          role = ?,
                          email_verified = true,
                          email_verified_at = CURRENT_TIMESTAMP
                        WHERE id = ? OR lower(email) = lower(?)
                        """,
                        E2E_ADMIN_EMAIL,
                        hash,
                        "E2E Admin",
                        UserRole.ADMIN.name(),
                        E2E_ADMIN_ID,
                        E2E_ADMIN_EMAIL);
        if (updated > 0) {
            log.info("E2E profile: ensured admin user {}", E2E_ADMIN_EMAIL);
            return;
        }
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO users (id, email, password_hash, name, role, created_at, email_verified, email_verified_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, true, CURRENT_TIMESTAMP)
                    """,
                    E2E_ADMIN_ID,
                    E2E_ADMIN_EMAIL,
                    hash,
                    "E2E Admin",
                    UserRole.ADMIN.name());
            log.info("E2E profile: seeded admin user {}", E2E_ADMIN_EMAIL);
        } catch (DataIntegrityViolationException duplicate) {
            jdbcTemplate.update(
                    """
                    UPDATE users SET
                      email = ?,
                      password_hash = ?,
                      name = ?,
                      role = ?,
                      email_verified = true,
                      email_verified_at = CURRENT_TIMESTAMP
                    WHERE id = ? OR lower(email) = lower(?)
                    """,
                    E2E_ADMIN_EMAIL,
                    hash,
                    "E2E Admin",
                    UserRole.ADMIN.name(),
                    E2E_ADMIN_ID,
                    E2E_ADMIN_EMAIL);
            log.info("E2E profile: reconciled admin user after concurrent insert");
        }
    }
}
