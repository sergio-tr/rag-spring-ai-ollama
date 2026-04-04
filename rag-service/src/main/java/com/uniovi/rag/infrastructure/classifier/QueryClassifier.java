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

    @Override
    QueryType classify(String query);
}
