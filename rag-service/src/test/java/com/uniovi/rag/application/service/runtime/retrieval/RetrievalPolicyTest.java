package com.uniovi.rag.application.service.runtime.retrieval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalPolicyTest {

    @Test
    void denseFetchLimit_min50_whenTopKBelow10() {
        assertThat(RetrievalPolicy.denseFetchLimit(5)).isEqualTo(50);
    }

    @Test
    void denseFetchLimit_capsAt500() {
        assertThat(RetrievalPolicy.denseFetchLimit(100)).isEqualTo(500);
    }

    @Test
    void denseFetchLimit_zeroFallsBackTo10xMultiplier() {
        assertThat(RetrievalPolicy.denseFetchLimit(0)).isEqualTo(50);
    }

    @Test
    void rrfK_is60() {
        assertThat(RetrievalPolicy.RRF_K).isEqualTo(60);
    }
}
