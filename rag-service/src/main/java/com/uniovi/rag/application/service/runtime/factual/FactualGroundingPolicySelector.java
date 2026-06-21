package com.uniovi.rag.application.service.runtime.factual;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import java.util.List;
import java.util.Optional;

public final class FactualGroundingPolicySelector {

    private FactualGroundingPolicySelector() {}

    public static AnswerGroundingPolicy selectPolicy(
            String question,
            boolean documentBound,
            QueryType labQueryTypeExpected,
            Optional<String> requiredDateIso,
            List<String> topicPhrases,
            Optional<String> requiredEntity,
            Optional<FactualQuestionConstraints.NumericConstraint> numericConstraint,
            boolean absenceQuestion) {
        if (absenceQuestion) {
            return AnswerGroundingPolicy.NEGATIVE_EVIDENCE;
        }
        if (!documentBound) {
            return AnswerGroundingPolicy.DEFAULT_RETRIEVAL_GROUNDED;
        }
        if (labQueryTypeExpected != null) {
            AnswerGroundingPolicy fromType = fromQueryType(labQueryTypeExpected, requiredDateIso, topicPhrases, requiredEntity);
            if (fromType != null) {
                return fromType;
            }
        }
        if (requiredDateIso.isPresent() && (!topicPhrases.isEmpty() || requiredEntity.isPresent())) {
            return AnswerGroundingPolicy.STRICT_GROUNDED;
        }
        if (numericConstraint.isPresent() || looksNumericQuestion(question)) {
            return AnswerGroundingPolicy.NUMERIC_OR_DATE;
        }
        if (requiredDateIso.isPresent()) {
            return AnswerGroundingPolicy.NUMERIC_OR_DATE;
        }
        if (requiredEntity.isPresent() || !topicPhrases.isEmpty() || looksEntityTopicQuestion(question)) {
            return AnswerGroundingPolicy.ENTITY_OR_TOPIC;
        }
        return AnswerGroundingPolicy.DEFAULT_RETRIEVAL_GROUNDED;
    }

    private static AnswerGroundingPolicy fromQueryType(
            QueryType queryType,
            Optional<String> requiredDateIso,
            List<String> topicPhrases,
            Optional<String> requiredEntity) {
        return switch (queryType) {
            case COUNT_DOCUMENTS, COUNT_AND_EXPLAIN, GET_DURATION, COMPARE, GET_FIELD -> AnswerGroundingPolicy.NUMERIC_OR_DATE;
            case BOOLEAN_QUERY, FIND_PARAGRAPH, EXTRACT_ENTITIES -> AnswerGroundingPolicy.ENTITY_OR_TOPIC;
            case FILTER_AND_LIST -> requiredDateIso.isPresent() || !topicPhrases.isEmpty() || requiredEntity.isPresent()
                    ? AnswerGroundingPolicy.STRICT_GROUNDED
                    : AnswerGroundingPolicy.ENTITY_OR_TOPIC;
            case SUMMARIZE_MEETING, SUMMARIZE_TOPIC, DECISION_EXTRACTION -> null;
        };
    }

    private static boolean looksNumericQuestion(String question) {
        if (question == null) {
            return false;
        }
        String q = question.toLowerCase();
        return q.contains("cuánt")
                || q.contains("cuant")
                || q.contains("número")
                || q.contains("numero")
                || q.contains("duración")
                || q.contains("duracion")
                || q.contains("exactamente");
    }

    private static boolean looksEntityTopicQuestion(String question) {
        if (question == null) {
            return false;
        }
        String q = question.toLowerCase();
        return q.contains("confirma si")
                || q.contains("verifica si")
                || q.contains("respecto a")
                || q.contains("mencion")
                || q.contains("qué se dijo")
                || q.contains("que se dijo");
    }
}
