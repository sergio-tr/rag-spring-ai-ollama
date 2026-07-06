package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Detects syntactically incomplete Spanish queries that must not receive invented answers. */
public final class IncompleteQueryHeuristics {

    public enum Reason {
        TRAILING_PREPOSITION,
        TRAILING_RELATIVE_CLAUSE,
        INCOMPLETE_COUNT_FILTER
    }

    public record Signal(Reason reason, List<String> missingFields, String traceNote) {}

    private static final Pattern TRAILING_DANGLING_PREPOSITION =
            Pattern.compile(
                    ".*\\b(en|sobre|de|del|al|para|con|sin|cuando|donde|que)\\s*\\??\\s*$",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern TRAILING_RELATIVE_CLAUSE =
            Pattern.compile(
                    ".*\\b(en las que|en los que|en la que|en el que|las que|los que)\\s*\\??\\s*$",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern INCOMPLETE_COUNT_OR_FILTER =
            Pattern.compile(
                    ".*\\b(?:cuenta|cuentas|cuente|contar|lista|listar|listame|enumera|indica|dime)\\b"
                            + ".*\\b(?:actas?|reuniones?)\\b"
                            + ".*\\b(?:en las que|en los que|en la que|en el que|donde|que)\\s*\\??\\s*$",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private IncompleteQueryHeuristics() {}

    public static Optional<Signal> detect(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Optional.of(
                    new Signal(
                            Reason.TRAILING_PREPOSITION,
                            List.of("query_scope"),
                            "incomplete_query_empty"));
        }
        String trimmed = rawText.trim();
        if (INCOMPLETE_COUNT_OR_FILTER.matcher(trimmed).matches()) {
            return Optional.of(
                    new Signal(
                            Reason.INCOMPLETE_COUNT_FILTER,
                            List.of("filter_condition"),
                            "incomplete_query_count_filter"));
        }
        if (TRAILING_RELATIVE_CLAUSE.matcher(trimmed).matches()) {
            return Optional.of(
                    new Signal(
                            Reason.TRAILING_RELATIVE_CLAUSE,
                            List.of("filter_condition"),
                            "incomplete_query_trailing_relative_clause"));
        }
        if (TRAILING_DANGLING_PREPOSITION.matcher(trimmed).matches()) {
            return Optional.of(
                    new Signal(
                            Reason.TRAILING_PREPOSITION,
                            List.of("time_reference"),
                            "incomplete_query_trailing_preposition"));
        }
        return Optional.empty();
    }

    /**
     * Max extra characters (beyond a proportional multiple of the raw query) a candidate may have and still be
     * treated as a "rewritten query" for completeness purposes. Query expansion (Q2E-style rationale, see
     * {@code MinuteDocumentStructureExpander}) intentionally appends long LLM-generated reasoning text to
     * {@code rewrittenQueryText} as extra embedding search terms; that text is not a user-facing query and must
     * not be evaluated by these trailing-word heuristics, or a coincidental trailing preposition in the
     * reasoning prose (e.g. truncated at "...sistemas de") false-positives a complete question as incomplete.
     */
    private static final int EXPANSION_BLOB_LENGTH_MULTIPLIER = 3;
    private static final int EXPANSION_BLOB_LENGTH_MARGIN = 40;

    public static Optional<Signal> detect(QueryPlan plan) {
        if (plan == null) {
            return Optional.empty();
        }
        String rawQuery = plan.rawUserQuery();
        int rawLen = rawQuery != null ? rawQuery.length() : 0;
        for (String candidate :
                List.of(rawQuery, plan.normalizedQueryText(), plan.rewrittenQueryText())) {
            if (isLikelyExpansionBlob(candidate, rawLen)) {
                continue;
            }
            Optional<Signal> signal = detect(candidate);
            if (signal.isPresent()) {
                return signal;
            }
        }
        return Optional.empty();
    }

    private static boolean isLikelyExpansionBlob(String candidate, int rawLen) {
        if (candidate == null) {
            return false;
        }
        int cap = Math.max(rawLen * EXPANSION_BLOB_LENGTH_MULTIPLIER, rawLen + EXPANSION_BLOB_LENGTH_MARGIN);
        return candidate.length() > cap;
    }

    public static AmbiguityAssessment toAmbiguityAssessment(Signal signal) {
        String reason =
                switch (signal.reason()) {
                    case TRAILING_PREPOSITION ->
                            "Incomplete query: trailing preposition without scope";
                    case TRAILING_RELATIVE_CLAUSE ->
                            "Incomplete query: trailing relative clause without predicate";
                    case INCOMPLETE_COUNT_FILTER ->
                            "Incomplete query: count/filter question missing condition";
                };
        return new AmbiguityAssessment(
                AmbiguityStatus.MISSING_INFORMATION, List.of(reason), signal.missingFields());
    }

    public static String abstentionMessage(Signal signal) {
        return switch (signal.reason()) {
            case TRAILING_PREPOSITION, TRAILING_RELATIVE_CLAUSE ->
                    "Tu pregunta parece incompleta. ¿A qué acta o reunión te refieres? Indica la fecha o el documento.";
            case INCOMPLETE_COUNT_FILTER ->
                    "Tu pregunta parece incompleta. ¿Qué condición o criterio debo usar para contar o filtrar las actas?";
        };
    }

    public static String abstentionMessage(String queryText) {
        return detect(queryText)
                .map(IncompleteQueryHeuristics::abstentionMessage)
                .orElse("Tu pregunta parece incompleta. ¿Puedes precisarla?");
    }
}
