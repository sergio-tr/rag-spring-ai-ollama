package com.uniovi.rag.testsupport;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Environment variables override {@code application-test.properties}. In GitHub Actions, org/repo variables
 * may set {@code RAG_JWT_SECRET} to an empty or short value, which makes {@code JwtService} fail during
 * context refresh. Empty {@code OTEL_EXPORTER_OTLP_ENDPOINT} yields invalid OTLP URLs like {@code /v1/traces}.
 * <p>
 * This initializer is test-classpath only ({@code META-INF/spring.factories}). It does not consult {@code prod}:
 * org-wide {@code SPRING_PROFILES_ACTIVE=prod} would otherwise skip the patch before {@code @ActiveProfiles("test")}
 * is visible on the environment, leaving broken {@code RAG_JWT_SECRET} from the process env.
 */
public final class SafeTestSecretsApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String TEST_JWT_SECRET = "test-secret-key-for-jwt-signing-must-be-long-enough-32";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();
        Map<String, Object> map = new LinkedHashMap<>();
        String jwt = env.getProperty("rag.jwt.secret");
        if (jwt == null || jwt.length() < 32) {
            map.put("rag.jwt.secret", TEST_JWT_SECRET);
        }
        String traces = env.getProperty("management.otlp.tracing.endpoint");
        if (traces != null && traces.startsWith("/")) {
            map.put("management.otlp.tracing.endpoint", "http://127.0.0.1:4318/v1/traces");
            map.put("management.otlp.metrics.export.url", "http://127.0.0.1:4318/v1/metrics");
        }
        if (!map.isEmpty()) {
            MutablePropertySources sources = env.getPropertySources();
            sources.addFirst(new MapPropertySource("ragSafeTestSecretsOverride", map));
        }
    }
}
