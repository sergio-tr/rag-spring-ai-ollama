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
        Objects.requireNonNull(normalizedQuery, "normalizedQuery");
        Objects.requireNonNull(classifierLabel, "classifierLabel");
        classifierQueryType = Objects.requireNonNullElseGet(classifierQueryType, Optional::empty);
        Objects.requireNonNull(classifierStatus, "classifierStatus");
        Objects.requireNonNull(entities, "entities");
        Objects.requireNonNull(rewrite, "rewrite");
        Objects.requireNonNull(queryIntent, "queryIntent");
        Objects.requireNonNull(expectedAnswerShape, "expectedAnswerShape");
        Objects.requireNonNull(ambiguityAssessment, "ambiguityAssessment");
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
    }
}

