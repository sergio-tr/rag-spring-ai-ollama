package com.uniovi.rag.infrastructure.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fails fast on insecure defaults when {@code prod} profile is active.
 */
@Component
@Profile("prod")
@Order(0)
public class ProductionSecurityValidator implements ApplicationRunner {

    private static final String PLACEHOLDER_SUBSTRING = "change-me";

    private final String jwtSecret;

    public ProductionSecurityValidator(@Value("${rag.jwt.secret:}") String jwtSecret) {
        this.jwtSecret = jwtSecret != null ? jwtSecret : "";
    }

    @Override
    public void run(ApplicationArguments args) {
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "Production requires rag.jwt.secret (or RAG_JWT_SECRET) with at least 32 characters.");
        }
        if (jwtSecret.toLowerCase().contains(PLACEHOLDER_SUBSTRING)) {
            throw new IllegalStateException(
                    "Production requires a non-placeholder JWT secret; set RAG_JWT_SECRET to a strong random value.");
        }
    }
}
