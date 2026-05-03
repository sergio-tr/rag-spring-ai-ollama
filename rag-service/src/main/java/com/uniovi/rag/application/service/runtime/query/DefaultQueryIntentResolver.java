package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.NormalizedQuery;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DefaultQueryIntentResolver implements QueryIntentResolver {

    @Override
    public QueryIntent resolve(
            NormalizedQuery normalized,
            Optional<QueryType> classifierQueryType,
            String classifierLabel,
            ClassifierStatus classifierStatus,
            StructuredRewriteResult rewrite,
            EntityExtractionResult entities) {

        // 1) Classifier wins when non-neutral and OK
        Optional<QueryType> cqt = Objects.requireNonNullElseGet(classifierQueryType, Optional::empty);
        if (classifierStatus == ClassifierStatus.OK && cqt.isPresent()) {
            return mapClassifierType(cqt.get());
        }

        // 2) Rewrite targetAction only when classifier is neutral/non-authoritative
        Optional<String> targetAction = rewrite != null ? rewrite.targetAction() : Optional.empty();
        Optional<QueryIntent> fromRewrite =
                targetAction.map(DefaultQueryIntentResolver::mapTargetAction).filter(i -> i != QueryIntent.UNKNOWN);
        if (fromRewrite.isPresent()) {
            return fromRewrite.get();
        }

        return heuristicIntent(normalized.normalizedText().toLowerCase(Locale.ROOT), entities);
    }

    private static QueryIntent heuristicIntent(String q, EntityExtractionResult entities) {
        if (q.contains("how many") || q.contains("cuánt") || q.startsWith("count ")) {
            return QueryIntent.COUNT;
        }
        if (q.startsWith("list ") || q.contains("lista") || q.contains("enumer")) {
            return QueryIntent.LIST;
        }
        if (q.contains("compare") || q.contains("compar")) {
            return QueryIntent.COMPARE;
        }
        if (q.contains("summar") || q.contains("resum")) {
            return QueryIntent.SUMMARIZE;
        }
        if (q.contains("explain") || q.contains("explica") || q.contains("por qué") || q.contains("porque")) {
            return QueryIntent.EXPLAIN;
        }
        if (q.contains("field") || q.contains("campo")) {
            return QueryIntent.EXTRACT_FIELD;
        }
        if (looksLikeYesNoQuestion(q)
                && entities.answerTypeHint().filter(h -> h.toLowerCase(Locale.ROOT).contains("boolean")).isPresent()) {
            return QueryIntent.BOOLEAN_CHECK;
        }
        return QueryIntent.UNKNOWN;
    }

    private static boolean looksLikeYesNoQuestion(String q) {
        return q.startsWith("is ")
                || q.startsWith("are ")
                || q.contains(" yes")
                || q.contains(" no")
                || q.contains("es ")
                || q.contains("son ")
                || (q.contains("¿") && q.contains("?"));
    }

    private static QueryIntent mapClassifierType(QueryType t) {
        return switch (t) {
            case COUNT_DOCUMENTS -> QueryIntent.COUNT;
            case FILTER_AND_LIST -> QueryIntent.LIST;
            case FIND_PARAGRAPH -> QueryIntent.FIND;
            case COUNT_AND_EXPLAIN, EXTRACT_ENTITIES -> QueryIntent.EXPLAIN;
            case SUMMARIZE_TOPIC, SUMMARIZE_MEETING -> QueryIntent.SUMMARIZE;
            case COMPARE -> QueryIntent.COMPARE;
            case GET_FIELD -> QueryIntent.EXTRACT_FIELD;
            case BOOLEAN_QUERY -> QueryIntent.BOOLEAN_CHECK;
            case DECISION_EXTRACTION, GET_DURATION -> QueryIntent.FIND;
        };
    }

    private static QueryIntent mapTargetAction(String action) {
        if (action == null || action.isBlank()) {
            return QueryIntent.UNKNOWN;
        }
        String a = action.trim().toUpperCase(Locale.ROOT);
        try {
            return QueryIntent.valueOf(a);
        } catch (IllegalArgumentException e) {
            return QueryIntent.UNKNOWN;
        }
    }
}

