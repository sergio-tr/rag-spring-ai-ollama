package com.uniovi.rag.testsupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresIntegrationTestSupportTest {

    @Test
    void replaceDatabaseName_swapsDatabaseSegmentAndPreservesQueryParams() {
        assertEquals(
                "jdbc:postgresql://localhost:5432/testdb",
                PostgresIntegrationTestSupport.replaceDatabaseName(
                        "jdbc:postgresql://localhost:5432/vectordb", "testdb"));
        assertEquals(
                "jdbc:postgresql://localhost:5432/flyway_verify?ssl=false",
                PostgresIntegrationTestSupport.replaceDatabaseName(
                        "jdbc:postgresql://localhost:5432/vectordb?ssl=false", "flyway_verify"));
    }

    @Test
    void replaceDatabaseName_rejectsInvalidUrls() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PostgresIntegrationTestSupport.replaceDatabaseName("invalid-url", "testdb"));
    }
}
