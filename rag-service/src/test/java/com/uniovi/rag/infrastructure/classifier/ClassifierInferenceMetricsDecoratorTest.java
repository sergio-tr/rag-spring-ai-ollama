package com.uniovi.rag.infrastructure.classifier;

import com.uniovi.rag.domain.model.QueryType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClassifierInferenceMetricsDecoratorTest {

    @Test
    void classifyInference_delegatesAndPreservesReliabilityMetadata() {
        QueryClassifier delegate = mock(QueryClassifier.class);
        when(delegate.classifyInference("count docs", "default"))
                .thenReturn(new ClassifierInferenceResponse("COUNT_DOCUMENTS", 0.88, "hash9", List.of()));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ClassifierInferenceMetricsDecorator decorator = new ClassifierInferenceMetricsDecorator(delegate, registry);

        ClassifierInferenceResponse response = decorator.classifyInference("count docs", "default");

        verify(delegate).classifyInference("count docs", "default");
        assertThat(response.confidence()).isEqualTo(0.88);
        assertThat(response.labelSetHash()).isEqualTo("hash9");
        assertThat(registry.find("rag_classifier_calls").tag("status", "success").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void classify_delegatesToUnderlyingClassifier() {
        QueryClassifier delegate = mock(QueryClassifier.class);
        when(delegate.classify("q", "m")).thenReturn(QueryType.BOOLEAN_QUERY);

        ClassifierInferenceMetricsDecorator decorator =
                new ClassifierInferenceMetricsDecorator(delegate, new SimpleMeterRegistry());

        assertThat(decorator.classify("q", "m")).isEqualTo(QueryType.BOOLEAN_QUERY);
        verify(delegate).classify("q", "m");
    }
}
