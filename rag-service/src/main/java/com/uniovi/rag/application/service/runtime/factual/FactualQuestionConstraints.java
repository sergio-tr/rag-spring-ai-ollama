package com.uniovi.rag.application.service.runtime.factual;

import com.uniovi.rag.domain.runtime.factual.FactualConstraintType;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record FactualQuestionConstraints(
        AnswerGroundingPolicy groundingPolicy,
        FactualConstraintType constraintType,
        Optional<String> requiredDateIso,
        List<String> topicPhrases,
        Optional<String> requiredEntity,
        Optional<NumericConstraint> numericConstraint,
        boolean absenceQuestion,
        boolean documentBound) {

    public FactualQuestionConstraints {
        groundingPolicy = Objects.requireNonNullElse(groundingPolicy, AnswerGroundingPolicy.DEFAULT_RETRIEVAL_GROUNDED);
        constraintType = Objects.requireNonNullElse(constraintType, FactualConstraintType.NONE);
        requiredDateIso = Objects.requireNonNullElse(requiredDateIso, Optional.empty());
        topicPhrases = List.copyOf(Objects.requireNonNullElse(topicPhrases, List.of()));
        requiredEntity = Objects.requireNonNullElse(requiredEntity, Optional.empty());
        numericConstraint = Objects.requireNonNullElse(numericConstraint, Optional.empty());
    }

    public record NumericConstraint(ComparatorKind kind, int value) {}

    public enum ComparatorKind {
        EXACTLY,
        AT_LEAST,
        AT_MOST
    }
}
