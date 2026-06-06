package com.uniovi.rag.domain.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductDemoModelTest {

    @Test
    void targetEmbeddingModels_documentedDimensions_matchReferenceMatrix() {
        assertThat(ProductDemoModel.MXBAI_EMBED_LARGE.documentedOutputDimensions()).hasValue(1024);
        assertThat(ProductDemoModel.NOMIC_EMBED_TEXT.documentedOutputDimensions()).hasValue(768);
        assertThat(ProductDemoModel.QWEN3_EMBEDDING.documentedOutputDimensions()).hasValue(1024);
    }

    @Test
    void targetEmbeddingModels_storeCompatibility_forDefault1024WidePgvector() {
        int store = 1024;
        assertThat(ProductDemoModel.MXBAI_EMBED_LARGE.fitsStoreEmbeddingDimension(store)).isTrue();
        assertThat(ProductDemoModel.QWEN3_EMBEDDING.fitsStoreEmbeddingDimension(store)).isTrue();
        assertThat(ProductDemoModel.NOMIC_EMBED_TEXT.fitsStoreEmbeddingDimension(store)).isFalse();
    }

    @Test
    void llmModels_haveNoDocumentedEmbeddingWidth() {
        for (ProductDemoModel m : ProductDemoModel.llmModels()) {
            assertThat(m.documentedOutputDimensions()).isEmpty();
        }
    }
}
