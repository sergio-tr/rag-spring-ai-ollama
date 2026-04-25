package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class DefaultExpectedAnswerShapeResolver implements ExpectedAnswerShapeResolver {

    @Override
    public ExpectedAnswerShape resolve(Optional<QueryType> classifierQueryType, EntityExtractionResult entities) {
        if (classifierQueryType != null && classifierQueryType.isPresent()) {
            return mapClassifierType(classifierQueryType.get());
        }
        if (entities != null && entities.answerTypeHint().isPresent()) {
            String hint = entities.answerTypeHint().get().toLowerCase(Locale.ROOT);
            if (hint.contains("boolean")) {
                return ExpectedAnswerShape.SCALAR_BOOLEAN;
            }
            if (hint.contains("number") || hint.contains("count")) {
                return ExpectedAnswerShape.SCALAR_COUNT;
            }
            if (hint.contains("list")) {
                return ExpectedAnswerShape.LIST;
            }
            if (hint.contains("decision")) {
                return ExpectedAnswerShape.DECISION_EXTRACTION;
            }
            if (hint.contains("field")) {
                return ExpectedAnswerShape.FIELD_VALUE;
            }
            if (hint.contains("comparison")) {
                return ExpectedAnswerShape.COMPARISON;
            }
            if (hint.contains("text")) {
                return ExpectedAnswerShape.PARAGRAPH;
            }
        }
        return ExpectedAnswerShape.UNKNOWN;
    }

    private static ExpectedAnswerShape mapClassifierType(QueryType t) {
        return switch (t) {
            case BOOLEAN_QUERY -> ExpectedAnswerShape.SCALAR_BOOLEAN;
            case COUNT_DOCUMENTS -> ExpectedAnswerShape.SCALAR_COUNT;
            case FILTER_AND_LIST, EXTRACT_ENTITIES -> ExpectedAnswerShape.LIST;
            case FIND_PARAGRAPH -> ExpectedAnswerShape.PARAGRAPH;
            case SUMMARIZE_TOPIC, SUMMARIZE_MEETING -> ExpectedAnswerShape.SUMMARY;
            case DECISION_EXTRACTION -> ExpectedAnswerShape.DECISION_EXTRACTION;
            case GET_FIELD -> ExpectedAnswerShape.FIELD_VALUE;
            case COMPARE -> ExpectedAnswerShape.COMPARISON;
            case COUNT_AND_EXPLAIN, GET_DURATION -> ExpectedAnswerShape.PARAGRAPH;
        };
    }
}

