package com.uniovi.rag.migration;

import com.uniovi.rag.testsupport.PostgresIntegrationTestSupport;
import com.uniovi.rag.testsupport.PostgresIntegrationTestSupport.PostgresBinding;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Applies Flyway to a clean Postgres, then verifies relational invariants for multi-tenant chat data
 * (users → projects → conversations) via JDBC (Fase C.1 of the test implementation plan).
 */
@EnabledIf(
        value = "com.uniovi.rag.testsupport.TestEnvironment#isIsolatedFlywayPostgresAvailable",
        disabledReason = "Postgres admin URL or Docker required for isolated Postgres")
class ProjectConversationFlywayJdbcIntegrationTest {

    private static PostgresBinding postgresBinding;
    private static DataSource dataSource;

    @BeforeAll
    static void startPostgres() {
        postgresBinding =
                PostgresIntegrationTestSupport.startIsolatedDatabase("api_v5_jdbc", "testcontainers-vectordb-init.sql");
        dataSource = postgresBinding.dataSource();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @AfterAll
    static void stopPostgres() {
        if (postgresBinding != null) {
            postgresBinding.cleanup().run();
        }
    }

    @Test
    void userProjectAndConversation_rowsObeyForeignKeys() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO users (id, email, password_hash, role) VALUES (?, ?, ?, ?)",
                userId,
                "jdbc-int@test.local",
                "{noop}test",
                "USER");
        jdbc.update(
                "INSERT INTO projects (id, owner_id, name, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())",
                projectId,
                userId,
                "Integration Project");
        jdbc.update(
                "INSERT INTO conversations (id, user_id, project_id, title, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, NOW(), NOW())",
                convId,
                userId,
                projectId,
                "Thread");

        Integer n =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM conversations c "
                                + "JOIN projects p ON c.project_id = p.id "
                                + "JOIN users u ON c.user_id = u.id WHERE c.id = ?",
                        Integer.class,
                        convId);
        assertEquals(1, n);
    }
}
