package com.uniovi.rag.testsupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEnvironmentTest {

    @Test
    void jdbcAndFlywayPredicatesTrackLocalPostgresAvailability() {
        boolean springBoot = TestEnvironment.isSpringBootPostgresAvailable();
        boolean jdbc = TestEnvironment.isJdbcIntegrationTestAvailable();
        boolean flyway = TestEnvironment.isIsolatedFlywayPostgresAvailable();

        if (springBoot) {
            assertTrue(jdbc, "reachable Spring Boot Postgres should enable JDBC integration tests");
            assertTrue(flyway, "reachable Spring Boot Postgres should enable isolated Flyway tests");
        }
    }
}
