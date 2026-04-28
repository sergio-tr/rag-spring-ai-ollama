package com.uniovi.rag.domain.runtime.query;

import com.uniovi.rag.domain.model.QueryType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Internal-only accumulator used inside {@code QueryUnderstandingPipeline}.
 */
public record QueryUnderstandingResult(
        NormalizedQuery normalizedQuery,
        String classifierLabel,
        Optional<QueryType> classifierQueryType,
        ClassifierStatus classifierStatus,
        EntityExtractionResult entities,
        StructuredRewriteResult rewrite,
        QueryIntent queryIntent,
        ExpectedAnswerShape expectedAnswerShape,
        AmbiguityAssessment ambiguityAssessment,
        List<String> notes) {

    public QueryUnderstandingResult {
        normalizedQuery = Objects.requireNonNull(normalizedQuery, "normalizedQuery");
        classifierLabel = Objects.requireNonNull(classifierLabel, "classifierLabel");
        classifierQueryType = Objects.requireNonNull(classifierQueryType, "classifierQueryType");
        classifierStatus = Objects.requireNonNull(classifierStatus, "classifierStatus");
        entities = Objects.requireNonNull(entities, "entities");
        rewrite = Objects.requireNonNull(rewrite, "rewrite");
        queryIntent = Objects.requireNonNull(queryIntent, "queryIntent");
        expectedAnswerShape = Objects.requireNonNull(expectedAnswerShape, "expectedAnswerShape");
        ambiguityAssessment = Objects.requireNonNull(ambiguityAssessment, "ambiguityAssessment");
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
    }
}

