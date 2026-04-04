package com.uniovi.rag.testsupport;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pushes {@code spring.datasource.*} before the context refresh so JDBC auto-configuration and JPA can create
 * {@code DataSource} / {@code entityManagerFactory}. Registered via {@code META-INF/spring.factories} (test classpath).
 * <p>
 * {@link org.springframework.test.context.DynamicPropertySource} on an {@code @Import}ed test configuration is not
 * always applied early enough for all auto-config paths; this initializer applies the same JDBC / Testcontainers
 * rules globally for test classpath Spring contexts.
 */
public final class TestJdbcEnvironmentApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String PROPERTY_SOURCE_NAME = "ragTestJdbcEnvironment";
    private static final String USE_TC_ENV = "RAG_TEST_USE_TESTCONTAINERS_DATASOURCE";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (useTestcontainersPostgres()) {
            var pg = PostgresTestContainerHolder.getOrStart();
            map.put("spring.datasource.url", pg.getJdbcUrl());
            map.put("spring.datasource.username", pg.getUsername());
            map.put("spring.datasource.password", pg.getPassword());
        } else {
            map.put("spring.datasource.url", firstNonBlankEnv("SPRING_DATASOURCE_URL",
                    "jdbc:postgresql://localhost:5432/vectordb"));
            map.put("spring.datasource.username", firstNonBlankEnv("SPRING_DATASOURCE_USERNAME", "postgres"));
            map.put("spring.datasource.password", firstNonBlankEnv("SPRING_DATASOURCE_PASSWORD", "postgres"));
        }
        ConfigurableEnvironment env = context.getEnvironment();
        MutablePropertySources sources = env.getPropertySources();
        sources.addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, map));
    }

    /**
     * Explicit false disables TC; explicit true forces TC; otherwise prefer a pre-set JDBC URL (CI), else TC.
     */
    private static boolean useTestcontainersPostgres() {
        String explicit = System.getenv(USE_TC_ENV);
        if ("false".equalsIgnoreCase(explicit)) {
            return false;
        }
        if ("true".equalsIgnoreCase(explicit)) {
            return true;
        }
        String url = System.getenv("SPRING_DATASOURCE_URL");
        if (url != null && !url.isBlank()) {
            return false;
        }
        return true;
    }

    private static String firstNonBlankEnv(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }
}
