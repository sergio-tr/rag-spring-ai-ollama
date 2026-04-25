package com.uniovi.rag.infrastructure.bootstrap;

import com.uniovi.rag.domain.UserRole;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntityFactory;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Ensures an ADMIN user exists for Playwright admin scenarios when profile {@code e2e} is active.
 * Credentials are for CI/local demo only — never enable this profile in production.
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

    private final UserRepository userRepository;

    public E2eAdminUserSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmailIgnoreCase(E2E_ADMIN_EMAIL).isPresent()) {
            return;
        }
        UserEntity u = UserEntityFactory.newUser(
                E2E_ADMIN_EMAIL,
                "E2E Admin",
                "{noop}" + E2E_ADMIN_PASSWORD,
                UserRole.ADMIN,
                Instant.now());
        u.setId(E2E_ADMIN_ID);
        try {
            userRepository.saveAndFlush(u);
            log.info("E2E profile: seeded admin user {}", E2E_ADMIN_EMAIL);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
            // Multiple runners/devtools restarts can race on startup; seeding must be best-effort and never crash the app.
            log.info("E2E profile: admin user already exists (seed race), continuing");
        }
    }
}
