package com.uniovi.rag.infrastructure.classifier;

import java.util.List;
import java.util.Objects;

/** Parsed classifier-service /classify payload with optional reliability metadata. */
public record ClassifierInferenceResponse(
        String queryType,
        Double confidence,
        String labelSetHash,
        List<TopPrediction> topPredictions) {

    public ClassifierInferenceResponse {
        topPredictions = topPredictions == null ? List.of() : List.copyOf(topPredictions);
    }

    public static ClassifierInferenceResponse ofLabel(String queryType) {
        return new ClassifierInferenceResponse(queryType, null, null, List.of());
    }

    public record TopPrediction(String queryType, Double confidence) {
        public TopPrediction {
            Objects.requireNonNull(queryType, "queryType");
        }
    }
}
