package com.uniovi.rag.testsupport;

import com.github.dockerjava.api.DockerClient;
import org.testcontainers.DockerClientFactory;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Shared predicates for JUnit {@link org.junit.jupiter.api.condition.EnabledIf} on integration tests.
 */
public final class TestEnvironment {

    private static final String DEFAULT_SPRING_BOOT_PG_URL = "jdbc:postgresql://localhost:5432/vectordb";

    private TestEnvironment() {
    }

    /**
     * True when full {@code @SpringBootTest} Postgres requirements are satisfied:
     * <ul>
     *     <li>{@code SPRING_DATASOURCE_URL} set and reachable (local scripts), or</li>
     *     <li>Postgres already listening on {@code localhost:5432/vectordb} (initializer fallback after TC), or</li>
     *     <li>Docker responds to a real ping (Testcontainers can start Postgres).</li>
     * </ul>
     * On GitHub Actions, we still probe first; if the service DB is not reachable yet, Docker can be used to
     * run Testcontainers instead of failing the Spring context.
     */
    @SuppressWarnings("unused") // referenced from @EnabledIf string
    public static boolean isSpringBootPostgresAvailable() {
        String user = firstNonBlankEnv("SPRING_DATASOURCE_USERNAME", "postgres");
        String pass = firstNonBlankEnv("SPRING_DATASOURCE_PASSWORD", "postgres");

        if (hasNonBlankEnv("SPRING_DATASOURCE_URL")) {
            if (canOpenPostgresJdbc(System.getenv("SPRING_DATASOURCE_URL"), user, pass)) {
                return true;
            }
            return canPingDockerDaemon();
        }

        if (canOpenPostgresJdbc(DEFAULT_SPRING_BOOT_PG_URL, user, pass)) {
            return true;
        }

        return canPingDockerDaemon();
    }

    /**
     * True when {@code INTEGRATION_JDBC_URL}, GitHub Actions service Postgres, or Docker is available
     * for JDBC integration tests.
     */
    @SuppressWarnings("unused")
    public static boolean isJdbcIntegrationTestAvailable() {
        return PostgresIntegrationTestSupport.isJdbcIntegrationDatabaseAvailable();
    }

    /**
     * True when Flyway JDBC tests can run on Testcontainers or a reachable local Postgres admin URL.
     */
    @SuppressWarnings("unused")
    public static boolean isIsolatedFlywayPostgresAvailable() {
        return PostgresIntegrationTestSupport.isIsolatedFlywayPostgresAvailable();
    }

    /**
     * True when the Docker API accepts a ping (stricter than {@link DockerClientFactory#isDockerAvailable()}
     * alone - avoids WSL / Docker Desktop half-configured states where strategies report success but
     * Testcontainers cannot run).
     */
    @SuppressWarnings("unused")
    public static boolean isDockerAvailable() {
        return canPingDockerDaemon();
    }

    private static boolean canPingDockerDaemon() {
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                return false;
            }
            DockerClient client = DockerClientFactory.instance().client();
            client.pingCmd().exec();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean canOpenPostgresJdbc(String url, String user, String pass) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            DriverManager.setLoginTimeout(5);
            try (Connection c = DriverManager.getConnection(url, user, pass)) {
                return c.isValid(3);
            }
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isGitHubActions() {
        return "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
    }

    private static boolean hasNonBlankEnv(String name) {
        String v = System.getenv(name);
        return v != null && !v.isBlank();
    }

    private static String firstNonBlankEnv(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }
}
