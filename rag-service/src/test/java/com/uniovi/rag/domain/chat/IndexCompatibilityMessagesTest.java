package com.uniovi.rag.domain.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IndexCompatibilityMessagesTest {

    @Test
    void forRequiredMaterializationStrategy_mapsKnownStrategies() {
        assertThat(IndexCompatibilityMessages.forRequiredMaterializationStrategy("DOCUMENT_LEVEL"))
                .isEqualTo("Requires DOCUMENT_LEVEL index");
        assertThat(IndexCompatibilityMessages.forRequiredMaterializationStrategy("chunk_level"))
                .isEqualTo("Requires CHUNK_LEVEL index");
        assertThat(IndexCompatibilityMessages.forRequiredMaterializationStrategy("HYBRID"))
                .isEqualTo("Requires HYBRID index");
    }

    @Test
    void forRequiredMaterializationStrategy_fallsBackForUnknownOrBlank() {
        assertThat(IndexCompatibilityMessages.forRequiredMaterializationStrategy(null))
                .isEqualTo("Requires a compatible index profile");
        assertThat(IndexCompatibilityMessages.forRequiredMaterializationStrategy(" "))
                .isEqualTo("Requires a compatible index profile");
        assertThat(IndexCompatibilityMessages.forRequiredMaterializationStrategy("CUSTOM"))
                .isEqualTo("Requires a compatible index profile");
    }
}
