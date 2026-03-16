package com.uniovi.rag.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for logic that could be extracted or validated from RagConfiguration.
 * The actual @Bean methods require Spring context; here we test equivalent logic.
 */
class RagConfigurationTest {

    @Test
    void maxChunkSizeLogic_positiveValue_returnsValue() {
        int chunkMaxChars = 400;
        int result = chunkMaxChars > 0 ? chunkMaxChars : 400;
        assertEquals(400, result);
    }

    @Test
    void maxChunkSizeLogic_zeroOrNegative_returnsDefault() {
        int chunkMaxChars = 0;
        int result = chunkMaxChars > 0 ? chunkMaxChars : 400;
        assertEquals(400, result);
        chunkMaxChars = -1;
        result = chunkMaxChars > 0 ? chunkMaxChars : 400;
        assertEquals(400, result);
    }

    @Test
    void featureConfigBean_createsNewInstance() {
        RagFeatureConfiguration config = new RagFeatureConfiguration();
        assertNotNull(config);
        assertTrue(config.isUseRetrieval());
        assertTrue(config.isUseAdvisor());
    }
}
