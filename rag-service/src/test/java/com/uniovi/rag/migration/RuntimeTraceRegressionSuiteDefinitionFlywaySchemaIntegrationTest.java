package com.uniovi.rag.migration;

import com.uniovi.rag.testsupport.PostgresIntegrationTestSupport;
import com.uniovi.rag.testsupport.PostgresIntegrationTestSupport.PostgresBinding;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JDBC checks for P33 schema (CHECK constraints, cascade, unique index) - no Spring context.
 */
@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isIsolatedFlywayPostgresAvailable",
        disabledReason = "Postgres admin URL or Docker required for isolated Postgres")
class RuntimeTraceRegressionSuiteDefinitionFlywaySchemaIntegrationTest {

    private static PostgresBinding postgresBinding;
    private static DataSource dataSource;

    @BeforeAll
    static void startPostgres() {
        postgresBinding =
                PostgresIntegrationTestSupport.startIsolatedDatabase("p33_schema", "testcontainers-vectordb-init.sql");
        dataSource = postgresBinding.dataSource();
        // Ensure schema resolution is stable across environments (search_path / Flyway defaults can differ).
        Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .defaultSchema("public")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        // Defensive diagnostics for CI: if schema resolution differs, fail early with actionable context.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String currentSchema = jdbc.queryForObject("SELECT current_schema()", String.class);
        String searchPath = jdbc.queryForObject("SHOW search_path", String.class);
        Integer defTableCount = jdbc.queryForObject(
                "SELECT COUNT(*)::int FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'runtime_trace_regression_suite_definition'",
                Integer.class);
        if (defTableCount == null || defTableCount != 1) {
            throw new IllegalStateException(
                    "Flyway migrations completed but expected table was not found. "
                            + "current_schema=" + currentSchema
                            + ", search_path=" + searchPath
                            + ", public.runtime_trace_regression_suite_definition count=" + defTableCount);
        }
    }

    @AfterAll
    static void stopPostgres() {
        if (postgresBinding != null) {
            postgresBinding.cleanup().run();
        }
    }

    @Test
    void uniqueUserName_enforced() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID userId = insertUser(jdbc);
        Instant now = Instant.now();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        insertDefinition(jdbc, a, userId, "same-name", now);
        assertThatThrownBy(() -> insertDefinition(jdbc, b, userId, "same-name", now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void check_byTraceIds_rejectsConversationColumn() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID userId = insertUser(jdbc);
        Instant now = Instant.now();
        UUID defId = UUID.randomUUID();
        insertDefinition(jdbc, defId, userId, "chk-trace", now);
        UUID entryId = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO runtime_trace_regression_suite_definition_entry "
                                                + "(id, definition_id, position, entry_kind, conversation_id) "
                                                + "VALUES (?, ?, 0, 'BY_TRACE_IDS', ?)",
                                        entryId,
                                        defId,
                                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cascadeDeleteDefinition_removesEntriesAndTraces() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID userId = insertUser(jdbc);
        Instant now = Instant.now();
        UUID defId = UUID.randomUUID();
        insertDefinition(jdbc, defId, userId, "cascade-me", now);
        UUID entryId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO runtime_trace_regression_suite_definition_entry "
                        + "(id, definition_id, position, entry_kind, conversation_id, created_at_from, created_at_to, workflow_name) "
                        + "VALUES (?, ?, 0, 'BY_TRACE_IDS', NULL, NULL, NULL, NULL)",
                entryId,
                defId);
        UUID traceRowId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO runtime_trace_regression_suite_definition_entry_trace (id, entry_id, position, trace_id) "
                        + "VALUES (?, ?, 0, ?)",
                traceRowId,
                entryId,
                UUID.randomUUID());

        jdbc.update("DELETE FROM runtime_trace_regression_suite_definition WHERE id = ?", defId);

        Integer entries = jdbc.queryForObject("SELECT COUNT(*)::int FROM runtime_trace_regression_suite_definition_entry", Integer.class);
        Integer traces = jdbc.queryForObject("SELECT COUNT(*)::int FROM runtime_trace_regression_suite_definition_entry_trace", Integer.class);
        assertThat(entries).isZero();
        assertThat(traces).isZero();
    }

    private static UUID insertUser(JdbcTemplate jdbc) {
        UUID userId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users (id, email, password_hash, role) VALUES (?, ?, ?, ?)",
                userId,
                userId + "@trace-regression.local",
                "{noop}test",
                "USER");
        return userId;
    }

    private static void insertDefinition(JdbcTemplate jdbc, UUID id, UUID userId, String name, Instant now) {
        // PgJDBC does not reliably infer java.time.Instant for TIMESTAMPTZ parameters in PreparedStatements.
        Timestamp ts = Timestamp.from(now);
        jdbc.update(
                "INSERT INTO runtime_trace_regression_suite_definition "
                        + "(id, user_id, name, description, schema_version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, NULL, 1, ?, ?)",
                id,
                userId,
                name,
                ts,
                ts);
    }
}
