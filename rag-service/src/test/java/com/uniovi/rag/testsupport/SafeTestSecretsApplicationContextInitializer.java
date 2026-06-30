package com.uniovi.rag.testsupport;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.Profiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test-classpath initializer ({@code META-INF/spring.factories} and
 * {@code META-INF/spring/org.springframework.context.ApplicationContextInitializer.imports}):
 * <ul>
 *     <li>JWT / OTLP: org env may break {@code rag.jwt.secret} or OTLP URLs.</li>
 *     <li>JDBC: sets {@code spring.datasource.*} with highest precedence so JPA creates {@code entityManagerFactory}
 *         (GitHub Actions service Postgres vs Testcontainers). Kept here so a single class is always registered;
 *         some CI setups did not load a second initializer from {@code spring.factories} reliably.</li>
 * </ul>
 */
public final class SafeTestSecretsApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(SafeTestSecretsApplicationContextInitializer.class);

    public static final String TEST_JWT_SECRET = "test-secret-key-for-jwt-signing-must-be-long-enough-32";

    private static final String JDBC_PROPERTY_SOURCE = "ragTestJdbcEnvironment";
    private static final String SECRETS_PROPERTY_SOURCE = "ragSafeTestSecretsOverride";
    private static final String TEST_LLM_PROPERTY_SOURCE = "ragTestLlmEnvironment";
    private static final String USE_TC_ENV = "RAG_TEST_USE_TESTCONTAINERS_DATASOURCE";
    private static final int JDBC_LOGIN_TIMEOUT_SECONDS = 3;
    private static final int EXTERNAL_DB_WAIT_SECONDS = 60;
    private static final int EXTERNAL_DB_WAIT_SLEEP_MILLIS = 2_000;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();
        MutablePropertySources sources = env.getPropertySources();

        Map<String, Object> secrets = new LinkedHashMap<>();
        String jwt = env.getProperty("rag.jwt.secret");
        if (jwt == null || jwt.length() < 32) {
            secrets.put("rag.jwt.secret", TEST_JWT_SECRET);
        }
        String traces = env.getProperty("management.otlp.tracing.endpoint");
        if (traces != null && traces.startsWith("/")) {
            secrets.put("management.otlp.tracing.endpoint", "http://127.0.0.1:4318/v1/traces");
            secrets.put("management.otlp.metrics.export.url", "http://127.0.0.1:4318/v1/metrics");
        }
        if (!secrets.isEmpty()) {
            sources.addFirst(new MapPropertySource(SECRETS_PROPERTY_SOURCE, secrets));
        }

        if (env.acceptsProfiles(Profiles.of("test"))) {
            // Docker dev (.env) and CI org env often set RAG_LLM_DEFAULT_PROVIDER=OPENAI_COMPATIBLE while
            // application-test.properties pins OLLAMA_NATIVE. Environment wins over profile files, which
            // breaks pgVectorStore wiring when openai-compatible default-embedding-model is unset.
            Map<String, Object> testLlm = new LinkedHashMap<>();
            testLlm.put("rag.llm.default-provider", "OLLAMA_NATIVE");
            testLlm.put("rag.llm.ollama.default-embedding-model", "mxbai-embed-large:latest");
            testLlm.put("rag.llm.openai-compatible.default-embedding-model", "qwen3-embedding:8b");
            testLlm.put("spring.ai.ollama.embedding.model", "mxbai-embed-large:latest");
            sources.addFirst(new MapPropertySource(TEST_LLM_PROPERTY_SOURCE, testLlm));
        }

        Map<String, Object> jdbc = jdbcPropertyMap();
        sources.addFirst(new MapPropertySource(JDBC_PROPERTY_SOURCE, jdbc));
    }

    private static Map<String, Object> jdbcPropertyMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (useTestcontainersPostgres()) {
            try {
                var pg = PostgresTestContainerHolder.getOrStart();
                map.put("spring.datasource.url", pg.getJdbcUrl());
                map.put("spring.datasource.username", pg.getUsername());
                map.put("spring.datasource.password", pg.getPassword());
                map.put("spring.flyway.clean-disabled", "false");
                map.put("spring.flyway.clean-on-validation-error", "true");
            } catch (Throwable t) {
                // Docker not available (common for @WebMvcTest on laptops) or Testcontainers failure — same JDBC
                // fallback as CI service Postgres / local defaults.
                log.debug("Testcontainers Postgres unavailable ({}), using env/default JDBC properties", t.toString());
                putEnvOrDefaultJdbc(map);
            }
        } else {
            putEnvOrDefaultJdbc(map);
        }
        return map;
    }

    private static void putEnvOrDefaultJdbc(Map<String, Object> map) {
        map.put("spring.datasource.url", firstNonBlankEnv("SPRING_DATASOURCE_URL",
                "jdbc:postgresql://localhost:5432/vectordb"));
        map.put("spring.datasource.username", firstNonBlankEnv("SPRING_DATASOURCE_USERNAME", "postgres"));
        map.put("spring.datasource.password", firstNonBlankEnv("SPRING_DATASOURCE_PASSWORD", "postgres"));
        map.put("spring.flyway.clean-disabled", "false");
        map.put("spring.flyway.clean-on-validation-error", "true");
    }

    private static boolean useTestcontainersPostgres() {
        String explicit = System.getenv(USE_TC_ENV);
        if ("false".equalsIgnoreCase(explicit)) {
            // CI commonly prefers an externally provisioned Postgres. If the service is still starting,
            // wait a bit so @SpringBootTest does not fail with "connection refused".
            String url = System.getenv("SPRING_DATASOURCE_URL");
            if (url != null && !url.isBlank()) {
                String user = firstNonBlankEnv("SPRING_DATASOURCE_USERNAME", "postgres");
                String pass = firstNonBlankEnv("SPRING_DATASOURCE_PASSWORD", "postgres");
                if (!waitForPostgres(url, user, pass)) {
                    // External DB not reachable yet. If Docker is available, prefer Testcontainers over failing.
                    return true;
                }
            }
            return false;
        }
        if ("true".equalsIgnoreCase(explicit)) {
            return true;
        }
        String url = System.getenv("SPRING_DATASOURCE_URL");
        if (url != null && !url.isBlank()) {
            String user = firstNonBlankEnv("SPRING_DATASOURCE_USERNAME", "postgres");
            String pass = firstNonBlankEnv("SPRING_DATASOURCE_PASSWORD", "postgres");
            // Prefer external DB when reachable (e.g. CI service Postgres),
            // but fall back to Testcontainers when the URL is set but the service is not yet ready.
            return !canOpenPostgresJdbc(url, user, pass);
        }
        return true;
    }

    private static String firstNonBlankEnv(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    private static boolean canOpenPostgresJdbc(String url, String user, String pass) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            DriverManager.setLoginTimeout(JDBC_LOGIN_TIMEOUT_SECONDS);
            try (Connection c = DriverManager.getConnection(url, user, pass)) {
                return c.isValid(2);
            }
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean waitForPostgres(String url, String user, String pass) {
        long deadline = System.currentTimeMillis() + (EXTERNAL_DB_WAIT_SECONDS * 1000L);
        while (System.currentTimeMillis() < deadline) {
            if (canOpenPostgresJdbc(url, user, pass)) {
                return true;
            }
            try {
                Thread.sleep(EXTERNAL_DB_WAIT_SLEEP_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
