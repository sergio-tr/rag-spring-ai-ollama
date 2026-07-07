package com.uniovi.rag.testsupport;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Shared Postgres binding for JDBC / Flyway integration tests: Testcontainers when Docker works,
 * otherwise a fresh database on the local CI-style Postgres (WSL-friendly).
 */
public final class PostgresIntegrationTestSupport {

    private static final String DEFAULT_SPRING_BOOT_PG_URL = "jdbc:postgresql://localhost:5432/vectordb";
    private static final String CI_DEFAULT_INTEGRATION_JDBC_URL = "jdbc:postgresql://localhost:5432/testdb";
    private static final String DEFAULT_ADMIN_JDBC_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String PGVECTOR_IMAGE = "pgvector/pgvector:0.8.2-pg16-bookworm";

    private PostgresIntegrationTestSupport() {
    }

    public record PostgresBinding(DataSource dataSource, Runnable cleanup) {}

    public static boolean isJdbcIntegrationDatabaseAvailable() {
        String user = jdbcUsername();
        String pass = jdbcPassword();
        String resolved = resolveJdbcIntegrationUrl();
        if (hasText(resolved) && canOpenPostgresJdbc(resolved, user, pass)) {
            return true;
        }
        if (isLocalPostgresAdminAvailable()) {
            return true;
        }
        return TestEnvironment.isDockerAvailable();
    }

    public static boolean isIsolatedFlywayPostgresAvailable() {
        return TestEnvironment.isDockerAvailable() || isLocalPostgresAdminAvailable();
    }

    public static boolean isLocalPostgresAdminAvailable() {
        String user = jdbcUsername();
        String pass = jdbcPassword();
        String adminUrl = resolveAdminJdbcUrl();
        return hasText(adminUrl) && canOpenPostgresJdbc(adminUrl, user, pass);
    }

    /**
     * Prefer explicit {@code INTEGRATION_JDBC_URL}; on GitHub Actions use workflow Postgres;
     * otherwise reuse reachable {@code testdb}, {@code SPRING_DATASOURCE_URL}, or {@code vectordb}.
     */
    public static String resolveJdbcIntegrationUrl() {
        String explicit = System.getenv("INTEGRATION_JDBC_URL");
        if (hasText(explicit)) {
            return explicit;
        }
        if (isGitHubActions()) {
            return CI_DEFAULT_INTEGRATION_JDBC_URL;
        }
        String user = jdbcUsername();
        String pass = jdbcPassword();
        if (canOpenPostgresJdbc(CI_DEFAULT_INTEGRATION_JDBC_URL, user, pass)) {
            return CI_DEFAULT_INTEGRATION_JDBC_URL;
        }
        if (isLocalPostgresAdminAvailable()) {
            return CI_DEFAULT_INTEGRATION_JDBC_URL;
        }
        String springUrl = resolveSpringDatasourceUrl();
        if (hasText(springUrl) && canOpenPostgresJdbc(springUrl, user, pass)) {
            return springUrl;
        }
        if (canOpenPostgresJdbc(DEFAULT_SPRING_BOOT_PG_URL, user, pass)) {
            return DEFAULT_SPRING_BOOT_PG_URL;
        }
        return null;
    }

    public static DataSource dataSourceForUrl(String jdbcUrl) {
        return new DriverManagerDataSource(jdbcUrl, jdbcUsername(), jdbcPassword());
    }

    public static PostgresBinding startJdbcIntegrationDatabase() {
        String externalUrl = resolveJdbcIntegrationUrl();
        String user = jdbcUsername();
        String pass = jdbcPassword();
        if (hasText(externalUrl)
                && CI_DEFAULT_INTEGRATION_JDBC_URL.equals(externalUrl)
                && isLocalPostgresAdminAvailable()
                && !isGitHubActions()) {
            recreateLocalDatabase("testdb");
            DataSource dataSource = dataSourceForUrl(externalUrl);
            applyClasspathScript(dataSource, "test-init.sql");
            return new PostgresBinding(dataSource, () -> dropLocalDatabase("testdb"));
        }
        if (hasText(externalUrl) && canOpenPostgresJdbc(externalUrl, user, pass)) {
            return new PostgresBinding(dataSourceForUrl(externalUrl), () -> {});
        }
        return startIsolatedDatabase("testdb", "test-init.sql", "test", "test");
    }

    public static PostgresBinding startIsolatedDatabase(String databaseName, String initClasspath) {
        return startIsolatedDatabase(databaseName, initClasspath, "test", "test");
    }

    public static PostgresBinding startIsolatedDatabase(
            String databaseName, String initClasspath, String username, String password) {
        if (TestEnvironment.isDockerAvailable()) {
            PostgreSQLContainer<?> postgres =
                    new PostgreSQLContainer<>(PGVECTOR_IMAGE)
                            .withDatabaseName(databaseName)
                            .withUsername(username)
                            .withPassword(password)
                            .withInitScript(initClasspath);
            postgres.start();
            DataSource dataSource =
                    new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            return new PostgresBinding(dataSource, postgres::stop);
        }
        recreateLocalDatabase(databaseName);
        String jdbcUrl = jdbcUrlForDatabase(databaseName);
        DataSource dataSource = new DriverManagerDataSource(jdbcUrl, jdbcUsername(), jdbcPassword());
        applyClasspathScript(dataSource, initClasspath);
        return new PostgresBinding(dataSource, () -> dropLocalDatabase(databaseName));
    }

    static void recreateLocalDatabase(String databaseName) {
        assertSafeDatabaseName(databaseName);
        String adminUrl = resolveAdminJdbcUrl();
        String user = jdbcUsername();
        String pass = jdbcPassword();
        try (Connection connection = DriverManager.getConnection(adminUrl, user, pass)) {
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '"
                                + databaseName
                                + "' AND pid <> pg_backend_pid()");
                statement.execute("DROP DATABASE IF EXISTS \"" + databaseName + "\"");
                statement.execute("CREATE DATABASE \"" + databaseName + "\"");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to recreate local database " + databaseName, e);
        }
    }

    static void dropLocalDatabase(String databaseName) {
        assertSafeDatabaseName(databaseName);
        String adminUrl = resolveAdminJdbcUrl();
        String user = jdbcUsername();
        String pass = jdbcPassword();
        try (Connection connection = DriverManager.getConnection(adminUrl, user, pass)) {
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '"
                                + databaseName
                                + "' AND pid <> pg_backend_pid()");
                statement.execute("DROP DATABASE IF EXISTS \"" + databaseName + "\"");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to drop local database " + databaseName, e);
        }
    }

    static void applyClasspathScript(DataSource dataSource, String initClasspath) {
        String scriptResource =
                "test-init.sql".equals(initClasspath) ? "test-init-jdbc-core.sql" : initClasspath;
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource(scriptResource));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }

    static String resolveAdminJdbcUrl() {
        String springUrl = resolveSpringDatasourceUrl();
        if (hasText(springUrl)) {
            return replaceDatabaseName(springUrl, "postgres");
        }
        if (canOpenPostgresJdbc(DEFAULT_ADMIN_JDBC_URL, jdbcUsername(), jdbcPassword())) {
            return DEFAULT_ADMIN_JDBC_URL;
        }
        if (canOpenPostgresJdbc(replaceDatabaseName(DEFAULT_SPRING_BOOT_PG_URL, "postgres"), jdbcUsername(), jdbcPassword())) {
            return replaceDatabaseName(DEFAULT_SPRING_BOOT_PG_URL, "postgres");
        }
        return DEFAULT_ADMIN_JDBC_URL;
    }

    static String jdbcUrlForDatabase(String databaseName) {
        String springUrl = resolveSpringDatasourceUrl();
        if (hasText(springUrl)) {
            return replaceDatabaseName(springUrl, databaseName);
        }
        return replaceDatabaseName(DEFAULT_SPRING_BOOT_PG_URL, databaseName);
    }

    static String replaceDatabaseName(String jdbcUrl, String databaseName) {
        int slash = jdbcUrl.lastIndexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("Invalid JDBC URL: " + jdbcUrl);
        }
        String suffix = "";
        int query = jdbcUrl.indexOf('?', slash);
        if (query >= 0) {
            suffix = jdbcUrl.substring(query);
        }
        return jdbcUrl.substring(0, slash + 1) + databaseName + suffix;
    }

    private static String resolveSpringDatasourceUrl() {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        return hasText(url) ? url : null;
    }

    private static void assertSafeDatabaseName(String databaseName) {
        if (!databaseName.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Unsafe database name: " + databaseName);
        }
    }

    private static boolean isGitHubActions() {
        return "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String jdbcUsername() {
        return firstNonBlankEnv("SPRING_DATASOURCE_USERNAME", "postgres");
    }

    private static String jdbcPassword() {
        return firstNonBlankEnv("SPRING_DATASOURCE_PASSWORD", "postgres");
    }

    private static String firstNonBlankEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return hasText(value) ? value : defaultValue;
    }

    private static boolean canOpenPostgresJdbc(String url, String user, String pass) {
        if (!hasText(url)) {
            return false;
        }
        try {
            DriverManager.setLoginTimeout(5);
            try (Connection connection = DriverManager.getConnection(url, user, pass)) {
                return connection.isValid(3);
            }
        } catch (Throwable ignored) {
            return false;
        }
    }
}
