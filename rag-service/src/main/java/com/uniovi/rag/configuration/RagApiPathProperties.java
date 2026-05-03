package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * HTTP path prefix for the authenticated product REST API.
 * Override with {@code RAG_API_PRODUCT_BASE_PATH}.
 */
@ConfigurationProperties(prefix = "rag.api")
@Validated
public class RagApiPathProperties {

    private static final String DEFAULT_PRODUCT_BASE_PATH = "/api/v5";

    /**
     * Prefix for authenticated product API (e.g. {@code /api/v5}).
     */
    private String productBasePath = DEFAULT_PRODUCT_BASE_PATH;

    public String getProductBasePath() {
        return productBasePath;
    }

    public void setProductBasePath(String productBasePath) {
        this.productBasePath = normalizePrefix(productBasePath, DEFAULT_PRODUCT_BASE_PATH);
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
