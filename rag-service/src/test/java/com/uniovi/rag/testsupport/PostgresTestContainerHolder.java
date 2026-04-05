package com.uniovi.rag.testsupport;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Lazily starts a single {@code pgvector} Postgres for local tests when
 * {@link SafeTestSecretsApplicationContextInitializer} chooses Testcontainers for JDBC (see {@code spring.factories}).
 */
public final class PostgresTestContainerHolder {

    private static volatile PostgreSQLContainer<?> container;

    private PostgresTestContainerHolder() {
    }

    public static PostgreSQLContainer<?> getOrStart() {
        if (container == null) {
            synchronized (PostgresTestContainerHolder.class) {
                if (container == null) {
                    PostgreSQLContainer<?> c = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                            .withDatabaseName("vectordb")
                            .withUsername("postgres")
                            .withPassword("postgres")
                            .withInitScript("testcontainers-vectordb-init.sql");
                    c.start();
                    container = c;
                }
            }
        }
        return container;
    }
}
