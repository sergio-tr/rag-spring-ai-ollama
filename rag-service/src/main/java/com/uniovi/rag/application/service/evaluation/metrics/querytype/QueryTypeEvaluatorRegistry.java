package com.uniovi.rag.application.service.evaluation.metrics.querytype;

import com.uniovi.rag.domain.model.QueryType;
import java.util.Optional;

public final class QueryTypeEvaluatorRegistry {

    private QueryTypeEvaluatorRegistry() {}

    public static StructuredEvaluationResult structuredEvaluation(
            QueryType queryType, String expectedAnswer, String actualAnswer, String answerMode) {
        return StructuredQueryTypeEvaluators.evaluateDetailed(queryType, expectedAnswer, actualAnswer, answerMode);
    }

    public static Optional<Double> structuredScore(
            QueryType queryType, String expectedAnswer, String actualAnswer, String answerMode) {
        return StructuredQueryTypeEvaluators.evaluate(queryType, expectedAnswer, actualAnswer, answerMode);
    }
}
