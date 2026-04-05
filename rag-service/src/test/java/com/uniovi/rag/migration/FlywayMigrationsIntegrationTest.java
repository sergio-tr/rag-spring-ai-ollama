package com.uniovi.rag.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Applies all Flyway scripts to a clean Postgres (with pgvector) and asserts the latest version.
 * Skips when Docker is unavailable (local dev without Docker); CI runners provide Docker.
 */
@EnabledIf(value = "com.uniovi.rag.testsupport.TestEnvironment#isDockerAvailable",
        disabledReason = "Docker required for isolated Flyway verification database")
class FlywayMigrationsIntegrationTest {

    private static PostgreSQLContainer<?> postgres;

    private static DataSource dataSource;

    @BeforeAll
    static void startPostgres() {
        postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                .withDatabaseName("flyway_verify")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("testcontainers-vectordb-init.sql");
        postgres.start();
        dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
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
