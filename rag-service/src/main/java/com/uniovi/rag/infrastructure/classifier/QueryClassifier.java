package com.uniovi.rag.infrastructure.classifier;

import com.uniovi.rag.application.port.ClassifierInferencePort;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.infrastructure.observability.Loggable;

/**
 * Query-time classifier used by the RAG pipeline (tracing + {@link ClassifierInferencePort}).
 */
public interface QueryClassifier extends ClassifierInferencePort, Loggable {

    @Override
    String classifyWithText(String query);

    default String classifyWithText(String query, String modelId) {
        return classifyWithText(query);
    }

    @Override
    QueryType classify(String query);

    default QueryType classify(String query, String modelId) {
        return classify(query);
    }

    default ClassifierInferenceResponse classifyInference(String query, String modelId) {
        String raw = classifyWithText(query, modelId);
        return raw == null || raw.isBlank() ? null : ClassifierInferenceResponse.ofLabel(raw);
    }
}
