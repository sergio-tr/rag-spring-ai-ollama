package com.uniovi.rag.application.service.runtime.retrieval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SparseQueryNormalizerTest {

    @Test
    void normalize_collapsesWhitespaceAndTrimsPunctuation() {
        assertThat(SparseQueryNormalizer.normalize("  zxqv_sparse_probe_token!!!  "))
                .isEqualTo("zxqv_sparse_probe_token");
    }

    @Test
    void normalize_blankReturnsEmpty() {
        assertThat(SparseQueryNormalizer.normalize("   ")).isEmpty();
        assertThat(SparseQueryNormalizer.normalize(null)).isEmpty();
    }
}
