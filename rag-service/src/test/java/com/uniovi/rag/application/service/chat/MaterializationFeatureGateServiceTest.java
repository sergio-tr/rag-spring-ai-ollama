package com.uniovi.rag.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaterializationFeatureGateServiceTest {

    @Test
    void structuredSearch_disablesRetrievalStackOnly() {
        assertThat(MaterializationFeatureGateService.structuredSearchDisable("useRetrieval"))
                .isPresent()
                .get()
                .extracting(d -> d.reasonCode())
                .isEqualTo(MaterializationFeatureGateService.STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED);
        assertThat(MaterializationFeatureGateService.structuredSearchDisable("naiveFullCorpusInPromptEnabled"))
                .isPresent();
        assertThat(MaterializationFeatureGateService.structuredSearchDisable("useAdvisor")).isPresent();
        assertThat(MaterializationFeatureGateService.structuredSearchDisable("rankerEnabled")).isPresent();
        assertThat(MaterializationFeatureGateService.structuredSearchDisable("postRetrievalEnabled")).isPresent();
    }

    @Test
    void structuredSearch_doesNotDisableQueryExpansion() {
        assertThat(MaterializationFeatureGateService.structuredSearchDisable("expansionEnabled")).isEmpty();
    }

    @Test
    void structuredSearch_doesNotDisableDirectPathFeatures() {
        assertThat(MaterializationFeatureGateService.structuredSearchDisable("nerEnabled")).isEmpty();
        assertThat(MaterializationFeatureGateService.structuredSearchDisable("toolsEnabled")).isEmpty();
        assertThat(MaterializationFeatureGateService.structuredSearchDisable("functionCallingEnabled")).isEmpty();
        assertThat(MaterializationFeatureGateService.structuredSearchDisable("memoryEnabled")).isEmpty();
        assertThat(MaterializationFeatureGateService.structuredSearchDisable("clarificationEnabled")).isEmpty();
    }
}
