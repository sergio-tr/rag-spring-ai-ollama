package com.uniovi.rag.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.config.validation.CompatibilitySeverity;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.config.CompatibilityRulesConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CompatibilityValidator validator;

    @BeforeEach
    void setUp() {
        validator =
                new CompatibilityValidator(new CompatibilityRulesConfiguration().compatibilityRules());
    }

    private static RagConfig baselineFeatures() {
        RagFeatureConfiguration f = new RagFeatureConfiguration();
        f.setToolsEnabled(true);
        f.setMetadataEnabled(false);
        f.setPostRetrievalEnabled(false);
        f.setFunctionCallingEnabled(false);
        f.setUseRetrieval(true);
        f.setExpansionEnabled(false);
        return RagConfig.fromFeatureConfiguration(f, 10, 0.7, "llm", "emb", "classifier", "simple");
    }

    @Test
    void okWhenNoRuleViolations() {
        RagConfig cfg = baselineFeatures();
        CapabilitySet caps = CapabilitySet.fromRagConfig(cfg);
        CompatibilityResult r = validator.validate(caps, cfg);
        assertTrue(r.valid());
        assertEquals(CompatibilitySeverity.OK, r.severity());
        assertTrue(r.errors().isEmpty());
    }

    @Test
    void mutuallyExclusiveCapabilitiesProduceError() throws Exception {
        RagConfig cfg =
                RagConfig.applyJsonOverrides(
                        baselineFeatures(),
                        MAPPER.readTree(
                                "{\"functionCallingEnabled\": true, \"naiveFullCorpusInPromptEnabled\": true}"));
        CapabilitySet caps = CapabilitySet.fromRagConfig(cfg);
        CompatibilityResult r = validator.validate(caps, cfg);
        assertFalse(r.valid());
        assertEquals(CompatibilitySeverity.ERROR, r.severity());
        assertTrue(
                r.errors().stream().anyMatch(v -> "MUTUALLY_EXCLUSIVE".equals(v.code())));
    }

    @Test
    void metadataRequiresToolsError() {
        RagFeatureConfiguration f = new RagFeatureConfiguration();
        f.setToolsEnabled(false);
        f.setMetadataEnabled(true);
        f.setUseRetrieval(true);
        f.setPostRetrievalEnabled(false);
        RagConfig cfg =
                RagConfig.fromFeatureConfiguration(f, 10, 0.7, "llm", "emb", "classifier", "simple");
        CapabilitySet caps = CapabilitySet.fromRagConfig(cfg);
        CompatibilityResult r = validator.validate(caps, cfg);
        assertFalse(r.valid());
        assertTrue(
                r.errors().stream().anyMatch(v -> "REQUIRES_CAPABILITY".equals(v.code())));
    }

    @Test
    void postRetrievalRequiresUseRetrievalError() {
        RagFeatureConfiguration f = new RagFeatureConfiguration();
        f.setToolsEnabled(true);
        f.setMetadataEnabled(false);
        f.setPostRetrievalEnabled(true);
        f.setUseRetrieval(false);
        RagConfig cfg =
                RagConfig.fromFeatureConfiguration(f, 10, 0.7, "llm", "emb", "classifier", "simple");
        CapabilitySet caps = CapabilitySet.fromRagConfig(cfg);
        CompatibilityResult r = validator.validate(caps, cfg);
        assertFalse(r.valid());
        assertTrue(
                r.errors().stream().anyMatch(v -> "REQUIRES_CAPABILITY".equals(v.code())));
    }

    @Test
    void numericTopKOutOfRangeProducesError() throws Exception {
        RagConfig cfg = RagConfig.applyJsonOverrides(baselineFeatures(), MAPPER.readTree("{\"topK\": 0}"));
        CapabilitySet caps = CapabilitySet.fromRagConfig(cfg);
        CompatibilityResult r = validator.validate(caps, cfg);
        assertFalse(r.valid());
        assertTrue(r.errors().stream().anyMatch(v -> "TOPK_RANGE".equals(v.code())));
    }

    @Test
    void structuredSearchWithRetrievalProducesError() throws Exception {
        RagConfig cfg =
                RagConfig.applyJsonOverrides(
                        baselineFeatures(),
                        MAPPER.readTree("{\"materializationStrategy\": \"STRUCTURED_SEARCH\"}"));
        CapabilitySet caps = CapabilitySet.fromRagConfig(cfg);
        CompatibilityResult r = validator.validate(caps, cfg);
        assertFalse(r.valid());
        assertTrue(
                r.errors().stream()
                        .anyMatch(v -> "STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED".equals(v.code())));
    }
}
