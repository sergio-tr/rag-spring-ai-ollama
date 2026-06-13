package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.infrastructure.classifier.ClassifierInferenceResponse;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TracedQueryClassifierTest {

    @Test
    void classifyInference_delegatesToUnderlyingClassifier() {
        QueryClassifier delegate = mock(QueryClassifier.class);
        when(delegate.classifyInference("count docs", "default"))
                .thenReturn(new ClassifierInferenceResponse("COUNT_DOCUMENTS", 0.91, "hash1", List.of()));

        TracedQueryClassifier traced = new TracedQueryClassifier(delegate, null);

        ClassifierInferenceResponse response = traced.classifyInference("count docs", "default");

        verify(delegate).classifyInference("count docs", "default");
        assertThat(response.queryType()).isEqualTo("COUNT_DOCUMENTS");
        assertThat(response.confidence()).isEqualTo(0.91);
        assertThat(response.labelSetHash()).isEqualTo("hash1");
    }
}
