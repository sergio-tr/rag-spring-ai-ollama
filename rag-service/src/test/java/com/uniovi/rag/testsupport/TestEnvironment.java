package com.uniovi.rag.testsupport;

import org.testcontainers.DockerClientFactory;

/**
 * Shared predicates for JUnit {@link org.junit.jupiter.api.condition.EnabledIf} on integration tests.
 */
public final class TestEnvironment {

    private TestEnvironment() {
    }

    /**
     * True when {@code SPRING_DATASOURCE_URL} is set (e.g. CI Postgres service) or Docker is available
     * for Testcontainers-backed {@code @SpringBootTest}.
     */
    @SuppressWarnings("unused") // referenced from @EnabledIf string
    public static boolean isSpringBootPostgresAvailable() {
        if (hasNonBlankEnv("SPRING_DATASOURCE_URL")) {
            return true;
        }
        return isDockerAvailable();
    }

    /**
     * True when {@code INTEGRATION_JDBC_URL}, GitHub Actions service Postgres, or Docker is available
     * for JDBC integration tests.
     */
    @SuppressWarnings("unused")
    public static boolean isJdbcIntegrationTestAvailable() {
        if (hasNonBlankEnv("INTEGRATION_JDBC_URL")) {
            return true;
        }
        if ("true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"))) {
            return true;
        }
        return isDockerAvailable();
    }

    /**
     * True when the Testcontainers Docker environment is usable (local Docker Desktop, CI Docker, etc.).
     */
    @SuppressWarnings("unused")
    public static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasNonBlankEnv(String name) {
        String v = System.getenv(name);
        return v != null && !v.isBlank();
    }
}
