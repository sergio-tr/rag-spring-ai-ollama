package com.uniovi.rag.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JDBC checks for P33 schema (CHECK constraints, cascade, unique index) — no Spring context.
 */
@EnabledIf(value = "com.uniovi.rag.testsupport.TestEnvironment#isDockerAvailable", disabledReason = "Docker required for isolated Postgres")
class RuntimeTraceRegressionSuiteDefinitionFlywaySchemaIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    private static DataSource dataSource;

    @BeforeAll
    static void startPostgres() {
        postgres =
                new PostgreSQLContainer<>("pgvector/pgvector:0.8.2-pg16-bookworm")
                        .withDatabaseName("p33_schema")
                        .withUsername("test")
                        .withPassword("test")
                        .withInitScript("testcontainers-vectordb-init.sql");
        postgres.start();
        dataSource =
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        // Ensure schema resolution is stable across environments (search_path / Flyway defaults can differ).
        Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .defaultSchema("public")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void uniqueUserName_enforced() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID userId = UUID.randomUUID();
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
        UUID userId = UUID.randomUUID();
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
        UUID userId = UUID.randomUUID();
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

    private static void insertDefinition(JdbcTemplate jdbc, UUID id, UUID userId, String name, Instant now) {
        jdbc.update(
                "INSERT INTO runtime_trace_regression_suite_definition "
                        + "(id, user_id, name, description, schema_version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, NULL, 1, ?, ?)",
                id,
                userId,
                name,
                now,
                now);
    }
}
