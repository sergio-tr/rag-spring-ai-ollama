package com.uniovi.rag.domain.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductDemoModelTest {

    @Test
    void targetEmbeddingModels_documentedDimensions_matchThesisMatrix() {
        assertThat(ProductDemoModel.MXBAI_EMBED_LARGE.documentedOutputDimensions()).hasValue(1024);
        assertThat(ProductDemoModel.NOMIC_EMBED_TEXT.documentedOutputDimensions()).hasValue(768);
        assertThat(ProductDemoModel.BGE_M3.documentedOutputDimensions()).hasValue(1024);
    }

    @Test
    void targetEmbeddingModels_storeCompatibility_forDefault1024WidePgvector() {
        int store = 1024;
        assertThat(ProductDemoModel.MXBAI_EMBED_LARGE.fitsStoreEmbeddingDimension(store)).isTrue();
        assertThat(ProductDemoModel.BGE_M3.fitsStoreEmbeddingDimension(store)).isTrue();
        assertThat(ProductDemoModel.NOMIC_EMBED_TEXT.fitsStoreEmbeddingDimension(store)).isFalse();
    }

    @Test
    void llmModels_haveNoDocumentedEmbeddingWidth() {
        for (ProductDemoModel m : ProductDemoModel.llmModels()) {
            assertThat(m.documentedOutputDimensions()).isEmpty();
        }
    }
}
