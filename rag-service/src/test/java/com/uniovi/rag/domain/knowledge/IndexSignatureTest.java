package com.uniovi.rag.domain.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndexSignatureTest {

    @Test
    void toHashHex_isStableForSameInputs() {
        IndexSignature a = IndexSignature.chunkDefaults("mxbai-embed-large", 400);
        IndexSignature b = IndexSignature.chunkDefaults("mxbai-embed-large", 400);
        assertThat(a.toHashHex()).isEqualTo(b.toHashHex());
        assertThat(a.toHashHex()).hasSize(64);
    }

    @Test
    void toHashHex_differsWhenChunkSizeChanges() {
        IndexSignature a = IndexSignature.chunkDefaults("mxbai-embed-large", 400);
        IndexSignature b = IndexSignature.chunkDefaults("mxbai-embed-large", 401);
        assertThat(a.toHashHex()).isNotEqualTo(b.toHashHex());
    }
}
