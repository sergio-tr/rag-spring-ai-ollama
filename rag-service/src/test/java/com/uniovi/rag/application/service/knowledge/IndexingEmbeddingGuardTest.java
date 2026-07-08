package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.configuration.RagIndexingEmbeddingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexingEmbeddingGuardTest {

    @Test
    void effectiveEmbedMaxChars_appliesProfileCapSafetyRatioAndMaxChunk() {
        var props = new RagIndexingEmbeddingProperties(1000, 400, true, 0.5);
        var guard = new IndexingEmbeddingGuard(props);
        assertThat(guard.effectiveEmbedMaxChars(800)).isEqualTo(400);
        assertThat(guard.effectiveEmbedMaxChars(200)).isEqualTo(200);
    }

    @Test
    void prepareForEmbedding_truncatesLongText() {
        var guard = new IndexingEmbeddingGuard(new RagIndexingEmbeddingProperties(2048, 400, true, 0.85));
        String longText = "word ".repeat(200);
        var safe = guard.prepareForEmbedding(longText, 50);
        assertThat(safe.truncated()).isTrue();
        assertThat(safe.text().length()).isLessThanOrEqualTo(50);
    }

    @Test
    void isContextLengthFailure_detectsOllamaMessage() {
        var guard = new IndexingEmbeddingGuard(new RagIndexingEmbeddingProperties(2048, 400, true, 0.85));
        RuntimeException e =
                new RuntimeException("[400] input length exceeds the context length");
        assertThat(guard.isContextLengthFailure(e)).isTrue();
    }

    @Test
    void properties_rejectInvalidSafetyRatio() {
        assertThatThrownBy(() -> new RagIndexingEmbeddingProperties(100, 50, true, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void effectiveWholeDocumentEmbedMaxChars_ignoresChunkCap() {
        var props = new RagIndexingEmbeddingProperties(2048, 400, true, 0.85);
        var guard = new IndexingEmbeddingGuard(props);
        // The 400-char chunk-splitting cap must not bind here: only maxInputChars * ratio applies.
        assertThat(guard.effectiveWholeDocumentEmbedMaxChars()).isEqualTo(1740);
        assertThat(guard.effectiveWholeDocumentEmbedMaxChars())
                .isGreaterThan(guard.effectiveEmbedMaxChars(400));
    }

    @Test
    void effectiveWholeDocumentEmbedMaxChars_stillFloorsAtSixtyFour() {
        var props = new RagIndexingEmbeddingProperties(50, 400, true, 0.5);
        var guard = new IndexingEmbeddingGuard(props);
        assertThat(guard.effectiveWholeDocumentEmbedMaxChars()).isEqualTo(64);
    }
}
