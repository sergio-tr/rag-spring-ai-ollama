package com.uniovi.rag.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Registers {@code spring.datasource.*} for {@code @SpringBootTest} via {@link DynamicPropertySource}.
 * <p>
 * This class is <strong>not</strong> gated by {@code @ConditionalOnProperty}: when the condition was on the
 * configuration class, Spring could skip the whole type and never run {@code @DynamicPropertySource}, leaving
 * tests without a reliable JDBC URL in CI (no {@code DataSource} / {@code entityManagerFactory}).
 * <p>
 * Resolution order:
 * <ul>
 *     <li>{@code RAG_TEST_USE_TESTCONTAINERS_DATASOURCE=false} → use {@code SPRING_DATASOURCE_*} (or defaults).</li>
 *     <li>{@code RAG_TEST_USE_TESTCONTAINERS_DATASOURCE=true} → start Testcontainers Postgres.</li>
 *     <li>Unset and non-blank {@code SPRING_DATASOURCE_URL} → use that URL (typical GitHub Actions service Postgres).</li>
 *     <li>Otherwise → Testcontainers (local dev with Docker).</li>
 * </ul>
 */
@TestConfiguration
public class TestcontainersDatasourceConfiguration {

    private static final String USE_TC_ENV = "RAG_TEST_USE_TESTCONTAINERS_DATASOURCE";

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        if (useTestcontainersPostgres()) {
            var pg = PostgresTestContainerHolder.getOrStart();
            registry.add("spring.datasource.url", pg::getJdbcUrl);
            registry.add("spring.datasource.username", pg::getUsername);
            registry.add("spring.datasource.password", pg::getPassword);
            return;
        }
        registry.add("spring.datasource.url", () -> firstNonBlankEnv("SPRING_DATASOURCE_URL",
                "jdbc:postgresql://localhost:5432/vectordb"));
        registry.add("spring.datasource.username", () -> firstNonBlankEnv("SPRING_DATASOURCE_USERNAME", "postgres"));
        registry.add("spring.datasource.password", () -> firstNonBlankEnv("SPRING_DATASOURCE_PASSWORD", "postgres"));
    }

    /**
     * Explicit false disables TC; explicit true forces TC; otherwise prefer a pre-set JDBC URL (CI), else TC.
     */
    static boolean useTestcontainersPostgres() {
        String explicit = System.getenv(USE_TC_ENV);
        if ("false".equalsIgnoreCase(explicit)) {
            return false;
        }
        if ("true".equalsIgnoreCase(explicit)) {
            return true;
        }
        if (hasNonBlankEnv("SPRING_DATASOURCE_URL")) {
            return false;
        }
        return true;
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
