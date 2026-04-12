package com.uniovi.rag.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Resolves the product REST base path for tests from {@code classpath:application.properties}
 * ({@code rag.api.product-base-path}), so MockMvc URLs stay aligned with configuration instead of
 * hardcoding {@code /api/v5}.
 */
public final class RagApiTestPaths {

    private static final String APPLICATION_PROPERTIES = "application.properties";
    private static final String PRODUCT_BASE_KEY = "rag.api.product-base-path";
    private static final String FALLBACK_PRODUCT_BASE = "/api/v5";

    private static volatile String cachedProductBase;

    private RagApiTestPaths() {
    }

    /**
     * Product API base path (e.g. {@code /api/v5}), loaded once from test {@code application.properties}.
     */
    public static String productBasePath() {
        String cached = cachedProductBase;
        if (cached != null) {
            return cached;
        }
        synchronized (RagApiTestPaths.class) {
            if (cachedProductBase != null) {
                return cachedProductBase;
            }
            Properties p = new Properties();
            try (InputStream in = RagApiTestPaths.class.getClassLoader().getResourceAsStream(APPLICATION_PROPERTIES)) {
                if (in != null) {
                    p.load(in);
                }
            } catch (IOException e) {
                cachedProductBase = FALLBACK_PRODUCT_BASE;
                return cachedProductBase;
            }
            String v = p.getProperty(PRODUCT_BASE_KEY, FALLBACK_PRODUCT_BASE).trim();
            if (v.isEmpty()) {
                v = FALLBACK_PRODUCT_BASE;
            }
            cachedProductBase = v;
            return cachedProductBase;
        }
    }

    /**
     * Full path for MockMvc: {@link #productBasePath()} + suffix. The suffix must start with {@code /}
     * unless it is empty (then the product base alone is returned).
     *
     * @param pathAfterProductBase path segment after the product base, e.g. {@code /config/schema}; use {@code ""} for the base only
     */
    public static String path(String pathAfterProductBase) {
        String base = productBasePath();
        if (pathAfterProductBase == null || pathAfterProductBase.isEmpty()) {
            return base;
        }
        if (!pathAfterProductBase.startsWith("/")) {
            return base + "/" + pathAfterProductBase;
        }
        return base + pathAfterProductBase;
    }
}
