package com.uniovi.rag.infrastructure.bootstrap;

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
 * Ensures at least one ADMIN user exists when profile {@code prod} is active.
 *
 * <p>If an admin already exists, does nothing (safe for upgrades). If no admin exists, this seeder requires
 * {@code rag.bootstrap.admin.email} and {@code rag.bootstrap.admin.password} to be set (typically via .env)
 * and creates the initial admin account.
 */
@Component
@Profile("prod")
@Order(10)
public class ProductionAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionAdminSeeder.class);

    private static final String SQL_COUNT_ADMINS = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'";

    private static final String SQL_INSERT_ADMIN =
            """
            INSERT INTO users (id, email, password_hash, name, role, created_at, email_verified, email_verified_at)
            VALUES (?, ?, ?, ?, 'ADMIN', CURRENT_TIMESTAMP, true, CURRENT_TIMESTAMP)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    private final String adminEmail;
    private final String adminPasswordPlain;
    private final String adminDisplayName;

    public ProductionAdminSeeder(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${rag.bootstrap.admin.email:}") String adminEmail,
            @Value("${rag.bootstrap.admin.password:}") String adminPasswordPlain,
            @Value("${rag.bootstrap.admin.name:Admin}") String adminDisplayName) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPasswordPlain = adminPasswordPlain;
        this.adminDisplayName = adminDisplayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer n = jdbcTemplate.queryForObject(SQL_COUNT_ADMINS, Integer.class);
        int adminCount = n != null ? n : 0;
        if (adminCount > 0) {
            log.info("Prod profile: admin users already exist (count={})", adminCount);
            return;
        }

        String email = adminEmail == null ? "" : adminEmail.trim().toLowerCase();
        if (email.isBlank() || adminPasswordPlain == null || adminPasswordPlain.isBlank()) {
            throw new IllegalStateException(
                    "Production requires an initial admin user. Set rag.bootstrap.admin.email and rag.bootstrap.admin.password.");
        }

        String hash = passwordEncoder.encode(adminPasswordPlain);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(SQL_INSERT_ADMIN, id, email, hash, adminDisplayName);
        log.info("Prod profile: seeded initial ADMIN user {}", email);
    }
}

