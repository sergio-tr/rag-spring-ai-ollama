package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * HTTP path prefixes for product (authenticated) and legacy (public) REST APIs.
 * Override with {@code RAG_API_PRODUCT_BASE_PATH} and {@code RAG_API_LEGACY_BASE_PATH}.
 */
@ConfigurationProperties(prefix = "rag.api")
@Validated
public class RagApiPathProperties {

    private static final String DEFAULT_PRODUCT_BASE_PATH = "/api/v5";
    private static final String DEFAULT_LEGACY_BASE_PATH = "/api/v4";

    /**
     * Prefix for authenticated product API (e.g. {@code /api/v5}).
     */
    private String productBasePath = DEFAULT_PRODUCT_BASE_PATH;

    /**
     * Prefix for legacy tooling API (e.g. {@code /api/v4}); not registered when {@code prod} profile is active.
     */
    private String legacyBasePath = DEFAULT_LEGACY_BASE_PATH;

    public String getProductBasePath() {
        return productBasePath;
    }

    public void setProductBasePath(String productBasePath) {
        this.productBasePath = normalizePrefix(productBasePath, DEFAULT_PRODUCT_BASE_PATH);
    }

    public String getLegacyBasePath() {
        return legacyBasePath;
    }

    public void setLegacyBasePath(String legacyBasePath) {
        this.legacyBasePath = normalizePrefix(legacyBasePath, DEFAULT_LEGACY_BASE_PATH);
    }

    private static String normalizePrefix(String raw, String defaultPrefix) {
        if (raw == null || raw.isBlank()) {
            return defaultPrefix;
        }
        String t = raw.trim();
        if (!t.startsWith("/")) {
            t = "/" + t;
        }
        if (t.length() > 1 && t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}
