package com.uniovi.rag.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Applies Flyway to a clean Postgres, then verifies relational invariants for multi-tenant chat data
 * (users → projects → conversations) via JDBC (Fase C.1 of the test implementation plan).
 */
@EnabledIf(value = "com.uniovi.rag.testsupport.TestEnvironment#isDockerAvailable",
        disabledReason = "Docker required for isolated Postgres")
class ProjectConversationFlywayJdbcIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    private static DataSource dataSource;

    @BeforeAll
    static void startPostgres() {
        postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                .withDatabaseName("api_v5_jdbc")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("testcontainers-vectordb-init.sql");
        postgres.start();
        dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
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
