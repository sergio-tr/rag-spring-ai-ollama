package com.uniovi.rag.testsupport;

import org.springframework.core.env.Environment;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Resolves the product REST base path for tests from {@code classpath:application.properties}
 * ({@code rag.api.product-base-path}), so MockMvc URLs stay aligned with configuration instead of
 * hardcoding {@code /api/v5}.
 * <p>
 * For {@code @WebMvcTest} / {@code @SpringBootTest} slices that override the base path via
 * {@code @TestPropertySource}, use {@link #productBasePath(Environment)} and {@link #path(Environment, String)}
 * so URLs match the active test context.
 */
public final class RagApiTestPaths {

    private static final String APPLICATION_PROPERTIES = "application.properties";
    private static final String PRODUCT_BASE_KEY = "rag.api.product-base-path";
    private static final String FALLBACK_PRODUCT_BASE = "/api/v5";

    private static volatile String cachedProductBase;

    private RagApiTestPaths() {
    }

    /**
     * Normalizes a configured product base (trim; strip trailing {@code /} except for root {@code /}).
     */
    public static String normalizeProductBasePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return FALLBACK_PRODUCT_BASE;
        }
        String v = raw.trim();
        while (v.length() > 1 && v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    /**
     * Joins a resolved product base with a suffix for MockMvc. The suffix should start with {@code /}
     * unless it is empty (then the product base alone is returned).
     *
     * @param resolvedProductBasePath already-normalized base, e.g. {@code /api/v5}
     * @param pathAfterProductBase    path segment after the product base, e.g. {@code /config/schema}; use {@code ""} for the base only
     */
    public static String joinProductPath(String resolvedProductBasePath, String pathAfterProductBase) {
        String base = normalizeProductBasePath(resolvedProductBasePath);
        if (pathAfterProductBase == null || pathAfterProductBase.isEmpty()) {
            return base;
        }
        if (!pathAfterProductBase.startsWith("/")) {
            return base + "/" + pathAfterProductBase;
        }
        return base + pathAfterProductBase;
    }

    /**
     * Product API base path from the Spring {@link Environment} when present (e.g. {@code @WebMvcTest}
     * with {@code @TestPropertySource}); otherwise falls back to {@link #productBasePath()}.
     */
    public static String productBasePath(Environment environment) {
        if (environment != null) {
            String v = environment.getProperty(PRODUCT_BASE_KEY);
            if (v != null && !v.isBlank()) {
                return normalizeProductBasePath(v);
            }
        }
        return productBasePath();
    }

    /**
     * Full path for MockMvc using the active Spring environment: {@link #productBasePath(Environment)} + suffix.
     *
     * @param pathAfterProductBase path segment after the product base, e.g. {@code /runtime-trace-regression-suite-runs}; use {@code ""} for the base only
     */
    public static String path(Environment environment, String pathAfterProductBase) {
        return joinProductPath(productBasePath(environment), pathAfterProductBase);
    }

    /**
     * Product API base path (e.g. {@code /api/v5}), loaded once from test {@code classpath:application.properties}.
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
            String v = normalizeProductBasePath(p.getProperty(PRODUCT_BASE_KEY, FALLBACK_PRODUCT_BASE));
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
        return joinProductPath(productBasePath(), pathAfterProductBase);
    }
}
