package com.uniovi.rag.testsupport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * When {@code rag.test.use-testcontainers-datasource=true} (default in {@code application-test.properties}),
 * wires {@code spring.datasource.*} to an embedded Testcontainers Postgres so {@code @SpringBootTest} runs
 * without a manual {@code localhost:5432} server. CI sets the property to false and provides a service container.
 */
@TestConfiguration
@ConditionalOnProperty(prefix = "rag.test", name = "use-testcontainers-datasource", havingValue = "true")
public class TestcontainersDatasourceConfiguration {

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        var pg = PostgresTestContainerHolder.getOrStart();
        registry.add("spring.datasource.url", pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
    }
}
