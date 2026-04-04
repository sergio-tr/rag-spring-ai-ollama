package com.uniovi.rag.application.port;

import com.uniovi.rag.domain.model.QueryType;

/**
 * Outbound port: runtime query classification (HTTP to classifier-service or fallback).
 */
public interface ClassifierInferencePort {

    QueryType classify(String query);

    String classifyWithText(String query);
}
