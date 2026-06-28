package com.uniovi.rag.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

/**
 * Verifies V61 seeds the canonical LAB allowlist and demotes bge-m3.
 */
@EnabledIf(value = "com.uniovi.rag.testsupport.TestEnvironment#isDockerAvailable",
        disabledReason = "Docker required for isolated Flyway verification database")
class AllowlistDefaultModelsFlywayIntegrationTest {

    private static final List<String> REQUIRED_ALLOWLIST = List.of(
            "mxbai-embed-large:latest",
            "nomic-embed-text:latest",
            "qwen3-embedding:latest",
            "gemma3:4b",
            "mistral:7b",
            "llama3.1:8b");

    private static PostgreSQLContainer<?> postgres;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void startPostgres() {
        postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.2-pg16-bookworm")
                .withDatabaseName("flyway_allowlist_verify")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("testcontainers-vectordb-init.sql");
        postgres.start();
        DataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void v61_seedsCanonicalAllowlistAndDemotesBgeM3() {
        List<Map<String, Object>> active = jdbc.queryForList(
                """
                SELECT name, type::text AS type, in_allowlist
                FROM allowed_model
                WHERE in_allowlist = TRUE
                ORDER BY type, name
                """);

        assertThat(active.stream().map(r -> (String) r.get("name")).toList())
                .containsAll(REQUIRED_ALLOWLIST);

        Integer bgeEnabled = jdbc.queryForObject(
                """
                SELECT COUNT(*)::int FROM allowed_model
                WHERE lower(name) IN ('bge-m3', 'bge-m3:latest') AND in_allowlist = TRUE
                """,
                Integer.class);
        assertThat(bgeEnabled).isZero();

        Integer qwenEmbedding = jdbc.queryForObject(
                """
                SELECT COUNT(*)::int FROM allowed_model
                WHERE lower(name) = 'qwen3-embedding:latest' AND type = 'EMBEDDING' AND in_allowlist = TRUE
                """,
                Integer.class);
        assertThat(qwenEmbedding).isEqualTo(1);
    }
}
