package com.uniovi.rag.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictRestJacksonConfigurationsTest {

    @Test
    void regressionSuiteStrictMapperFailsOnUnknownProperties() {
        ObjectMapper om = new RegressionSuiteRestJacksonConfiguration().regressionSuiteStrictObjectMapper();
        assertTrue(om.getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    @Test
    void replayBatchStrictMapperFailsOnUnknownProperties() {
        ObjectMapper om = new ReplayBatchRestJacksonConfiguration().replayBatchStrictObjectMapper();
        assertTrue(om.getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    @Test
    void traceComparisonBatchStrictMapperFailsOnUnknownProperties() {
        ObjectMapper om = new TraceComparisonBatchRestJacksonConfiguration().traceComparisonBatchStrictObjectMapper();
        assertTrue(om.getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    @Test
    void definitionMutationStrictMapperFailsOnUnknownProperties() {
        ObjectMapper om = new RegressionSuiteDefinitionMutationJacksonConfiguration().definitionMutationStrictObjectMapper();
        assertTrue(om.getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }
}
