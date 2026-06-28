package com.uniovi.rag.application.service.evaluation.metrics.querytype;

import com.uniovi.rag.domain.model.QueryType;
import java.util.Optional;

/** Deterministic or semantic score for a single query type (0..1 or empty if unavailable). */
public interface QueryTypeEvaluator {

    Optional<Double> evaluate(String expectedAnswer, String actualAnswer, String answerMode);
}
