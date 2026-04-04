package com.uniovi.rag.testsupport;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * Marker {@code @TestConfiguration} for {@code @Import} on full-stack tests. JDBC properties for
 * {@code @SpringBootTest} are registered early by {@link SafeTestSecretsApplicationContextInitializer}
 * ({@code META-INF/spring.factories}); this type carries no beans so imports stay stable across refactors.
 */
@TestConfiguration
public class TestcontainersDatasourceConfiguration {
}
