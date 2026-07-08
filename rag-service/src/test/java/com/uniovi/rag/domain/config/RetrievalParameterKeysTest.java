package com.uniovi.rag.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RetrievalParameterKeysTest {

    @Test
    void isRetrievalParameterKey_recognizesTopKAndSimilarityThreshold() {
        assertThat(RetrievalParameterKeys.isRetrievalParameterKey(RetrievalParameterKeys.TOP_K)).isTrue();
        assertThat(RetrievalParameterKeys.isRetrievalParameterKey(RetrievalParameterKeys.SIMILARITY_THRESHOLD))
                .isTrue();
        assertThat(RetrievalParameterKeys.isRetrievalParameterKey("metadataEnabled")).isFalse();
    }

    @Test
    void isPolicyMetadataKey_recognizesLockAndPolicyKeys() {
        assertThat(RetrievalParameterKeys.isPolicyMetadataKey(RetrievalParameterKeys.LOCK_RETRIEVAL_PARAMETERS))
                .isTrue();
        assertThat(RetrievalParameterKeys.isPolicyMetadataKey(RetrievalParameterKeys.RETRIEVAL_PARAMETER_POLICY))
                .isTrue();
        assertThat(RetrievalParameterKeys.isPolicyMetadataKey(RetrievalParameterKeys.TOP_K)).isFalse();
    }
}
