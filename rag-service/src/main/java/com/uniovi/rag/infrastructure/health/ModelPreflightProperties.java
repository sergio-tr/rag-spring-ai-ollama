package com.uniovi.rag.infrastructure.health;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Timeouts for fail-fast model availability checks (not full inference). */
@ConfigurationProperties(prefix = "rag.model-preflight")
public class ModelPreflightProperties {

    /** Max wait for lightweight chat/embedding probes during preflight. */
    private int probeTimeoutMs = 8_000;

    /** Optional positive cache TTL for catalog-only checks (0 = disabled). */
    private int catalogCacheTtlSeconds = 10;

    public int getProbeTimeoutMs() {
        return probeTimeoutMs;
    }

    public void setProbeTimeoutMs(int probeTimeoutMs) {
        this.probeTimeoutMs = probeTimeoutMs;
    }

    public int getCatalogCacheTtlSeconds() {
        return catalogCacheTtlSeconds;
    }

    public void setCatalogCacheTtlSeconds(int catalogCacheTtlSeconds) {
        this.catalogCacheTtlSeconds = catalogCacheTtlSeconds;
    }
}
