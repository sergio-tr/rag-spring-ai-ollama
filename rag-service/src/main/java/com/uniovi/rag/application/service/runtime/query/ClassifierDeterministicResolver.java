package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves {@link QueryType} from Spanish acta phrasing alone (no ML confidence required).
 * Used to keep matrix/evaluation questions on structured routes when the classifier is uncertain.
 */
public final class ClassifierDeterministicResolver {

    private ClassifierDeterministicResolver() {}

    public static Optional<QueryType> resolve(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return Optional.empty();
        }
        Optional<QueryType> explicit = ClassifierOverrides.matchRule(normalizedText);
        if (explicit.isPresent()) {
            return explicit;
        }
        QueryType adjusted =
                ClassifierOverrides.apply(normalizedText, ClassifierOverrides.RULE_SENTINEL_FOR_RESOLVE);
        if (adjusted != ClassifierOverrides.RULE_SENTINEL_FOR_RESOLVE) {
            return Optional.of(adjusted);
        }
        return Optional.empty();
    }

    public static boolean isMatrixStructuredQuestion(String normalizedText) {
        return resolve(normalizedText).isPresent();
    }

    public static String routingTraceNote(QueryType type, String source) {
        return "routing=" + type.name() + " source=" + source;
    }
}
