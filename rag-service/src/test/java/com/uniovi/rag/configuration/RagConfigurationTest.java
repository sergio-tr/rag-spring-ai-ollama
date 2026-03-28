package com.uniovi.rag.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Additional tests for {@link RagConfiguration} ({@link RagFeatureConfiguration} is registered via {@code @EnableConfigurationProperties}). */
class RagConfigurationTest {

    @Test
    void ragFeatureConfiguration_defaultsMatchExpected() {
        RagFeatureConfiguration config = new RagFeatureConfiguration();
        assertNotNull(config);
        assertTrue(config.isUseRetrieval());
        assertTrue(config.isUseAdvisor());
    }
}
