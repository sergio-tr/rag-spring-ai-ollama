package com.uniovi.rag.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.testsupport.PostgresIntegrationTestSupport;
import com.uniovi.rag.testsupport.PostgresIntegrationTestSupport.PostgresBinding;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Verifies latest Flyway migrations seed the canonical LAB allowlist (V61+) and thesis re-enable bge-m3 (V73).
 */
@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isIsolatedFlywayPostgresAvailable",
        disabledReason = "Postgres admin URL or Docker required for isolated Flyway verification database")
class AllowlistDefaultModelsFlywayIntegrationTest {

    private static final List<String> REQUIRED_ALLOWLIST = List.of(
            "mxbai-embed-large:latest",
            "nomic-embed-text:latest",
            "qwen3-embedding:latest",
            "gemma3:4b",
            "mistral:7b",
            "llama3.1:8b");

    private static PostgresBinding postgresBinding;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void startPostgres() {
        postgresBinding =
                PostgresIntegrationTestSupport.startIsolatedDatabase(
                        "flyway_allowlist_verify", "testcontainers-vectordb-init.sql");
        DataSource dataSource = postgresBinding.dataSource();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void stopPostgres() {
        if (postgresBinding != null) {
            postgresBinding.cleanup().run();
        }
    }

    @Test
    void latestMigrations_seedCanonicalAllowlistAndReenableBgeM3() {
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
                WHERE lower(name) = 'bge-m3' AND type = 'EMBEDDING' AND in_allowlist = TRUE
                """,
                Integer.class);
        assertThat(bgeEnabled).isEqualTo(1);

        Integer bgeLatestEnabled = jdbc.queryForObject(
                """
                SELECT COUNT(*)::int FROM allowed_model
                WHERE lower(name) = 'bge-m3:latest' AND in_allowlist = TRUE
                """,
                Integer.class);
        assertThat(bgeLatestEnabled).isZero();

        Integer qwenEmbedding = jdbc.queryForObject(
                """
                SELECT COUNT(*)::int FROM allowed_model
                WHERE lower(name) = 'qwen3-embedding:latest' AND type = 'EMBEDDING' AND in_allowlist = TRUE
                """,
                Integer.class);
        assertThat(qwenEmbedding).isEqualTo(1);
    }
}
