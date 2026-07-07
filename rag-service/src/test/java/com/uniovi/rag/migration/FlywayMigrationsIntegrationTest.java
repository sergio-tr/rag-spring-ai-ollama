package com.uniovi.rag.migration;

import com.uniovi.rag.testsupport.PostgresIntegrationTestSupport;
import com.uniovi.rag.testsupport.PostgresIntegrationTestSupport.PostgresBinding;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Applies all Flyway scripts to a clean Postgres (with pgvector) and asserts the latest version.
 * Uses Testcontainers when Docker works; otherwise a fresh database on local Postgres (WSL-friendly).
 */
@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isIsolatedFlywayPostgresAvailable",
        disabledReason = "Postgres admin URL or Docker required for isolated Flyway verification database")
class FlywayMigrationsIntegrationTest {

    private static PostgresBinding postgresBinding;

    private static DataSource dataSource;

    @BeforeAll
    static void startPostgres() {
        postgresBinding = PostgresIntegrationTestSupport.startIsolatedDatabase("flyway_verify", "testcontainers-vectordb-init.sql");
        dataSource = postgresBinding.dataSource();
    }

    @AfterAll
    static void stopPostgres() {
        if (postgresBinding != null) {
            postgresBinding.cleanup().run();
        }
    }

    @Test
    void migrationsApplyThroughLatestScript() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        MigrationInfo current = flyway.info().current();
        assertNotNull(current, "Flyway should report a current version after migrate()");
        assertNotNull(current.getVersion(), "Latest applied migration should be versioned");
        String expectedVersion = current.getVersion().getVersion();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String version = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1",
                String.class
        );
        assertEquals(expectedVersion, version);

        Integer usersCols = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'users'",
                Integer.class
        );
        assertTrue(usersCols != null && usersCols > 0);
    }
}
