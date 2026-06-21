package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;

import java.util.Locale;

/**
 * Rule-based adjustments after ML classifier output (Spanish phrasing hints).
 */
public final class ClassifierOverrides {

    /** Sentinel label never returned by {@link #apply(String, QueryType)} rules. */
    private static final QueryType RULE_SENTINEL = QueryType.DECISION_EXTRACTION;

    private ClassifierOverrides() {}

    /**
     * Returns a rule-matched query type when Spanish phrasing overrides apply, regardless of classifier output.
     */
    public static java.util.Optional<QueryType> matchRule(String query) {
        if (query == null || query.isBlank()) {
            return java.util.Optional.empty();
        }
        QueryType matched = apply(query, RULE_SENTINEL);
        return matched == RULE_SENTINEL ? java.util.Optional.empty() : java.util.Optional.of(matched);
    }

    /**
     * @param query       user text (typically expanded)
     * @param classified  label from {@link com.uniovi.rag.infrastructure.classifier.QueryClassifier}, may be null
     * @return overridden {@link QueryType}, or {@code classified} when no rule matches
     */
    public static QueryType apply(String query, QueryType classified) {
        if (query == null || query.isBlank()) {
            return classified;
        }
        String q = query.toLowerCase(Locale.ROOT);
        boolean actaContext =
                q.contains("acta") || q.contains("minuta") || q.contains("reunión") || q.contains("reunion");
        boolean dated = hasDated(q);

        boolean countCue =
                q.contains("cuántas")
                        || q.contains("cuantas")
                        || q.contains("en cuántas")
                        || q.contains("en cuantas")
                        || q.contains("cuántos")
                        || q.contains("cuantos");

        if (q.contains("en cuántas actas aparece")
                || q.contains("en cuantas actas aparece")
                || q.contains("en cuántas actas particip")
                || q.contains("en cuantas actas particip")) {
            return QueryType.COUNT_DOCUMENTS;
        }

        // Presence / verification phrasing → boolean-style answer over generic summarization
        if (q.contains("confirma si")
                || q.contains("verifica si")
                || q.contains("comprueba si")
                || (!countCue
                        && (q.contains("aparece") || q.contains("figura"))
                        && (q.contains("acta") || q.contains("reunión") || q.contains("reunion")))) {
            return QueryType.BOOLEAN_QUERY;
        }

        if (dated && (q.contains("duración")
                || q.contains("duracion")
                || q.contains("duration")
                || q.contains("cuánto dur")
                || q.contains("cuanto dur"))) {
            return QueryType.GET_DURATION;
        }

        if (dated && (q.contains("resume") || q.contains("resum"))) {
            return QueryType.SUMMARIZE_MEETING;
        }

        if (dated
                && (q.contains("cuántas personas")
                        || q.contains("cuantas personas")
                        || q.contains("cuántos asistentes")
                        || q.contains("cuantos asistentes")
                        || q.contains("quién asistió")
                        || q.contains("quien asistio"))) {
            return QueryType.GET_FIELD;
        }

        if (dated && (q.contains("orden del día") || q.contains("orden del dia") || q.contains("puntos del orden"))) {
            return QueryType.GET_FIELD;
        }

        if ((q.contains("menos de diez") || q.contains("menos de 10"))
                && (q.contains("particip") || q.contains("asistent") || q.contains("personas"))) {
            return QueryType.COUNT_DOCUMENTS;
        }

        if ((q.contains("cuántas") || q.contains("cuantas") || q.contains("en cuántas") || q.contains("en cuantas"))
                && q.matches(".*\\ben\\s+20\\d{2}\\b.*")
                && (q.contains("reunion") || q.contains("reunión") || q.contains("acta"))) {
            return QueryType.COUNT_DOCUMENTS;
        }

        if ((q.contains("cuántas") || q.contains("cuantas") || q.contains("en cuántas") || q.contains("en cuantas"))
                && (q.contains("presupuesto") || q.contains("presupuestos"))) {
            return QueryType.COUNT_DOCUMENTS;
        }

        if (q.contains("qué secciones comparten") || q.contains("que secciones comparten")) {
            return QueryType.EXTRACT_ENTITIES;
        }

        if (q.contains("en qué reuniones se mencion") || q.contains("en que reuniones se mencion")) {
            return QueryType.FILTER_AND_LIST;
        }

        if (q.contains("qué reuniones incluyeron") || q.contains("que reuniones incluyeron")) {
            return QueryType.FILTER_AND_LIST;
        }

        if (q.contains("en qué actas se mencion") || q.contains("en que actas se mencion")) {
            return QueryType.FIND_PARAGRAPH;
        }

        if (actaContext && dated) {
            if (q.contains("presidente") || q.contains("presidió") || q.contains("presidio")) {
                return QueryType.GET_FIELD;
            }
            if (q.contains("participantes")
                    || q.contains("participante")
                    || q.contains("asistentes")
                    || q.contains("asistente")
                    || q.contains("attendees")) {
                return QueryType.GET_FIELD;
            }
        }

        if (q.contains("cuántas actas")
                || q.contains("cuantas actas")
                || q.contains("en cuántas")
                || q.contains("en cuantas")
                || q.contains("número de actas")
                || q.contains("numero de actas")
                || q.contains("cuántas veces")
                || q.contains("cuantas veces")
                || (q.contains("cuántas") && q.contains("reunion"))
                || (q.contains("cuantas") && q.contains("reunion"))
                || (q.contains("cuántos") && actaContext)
                || (q.contains("cuantos") && actaContext)) {
            if (q.contains("qué se") || q.contains("que se") || q.contains("contexto") || q.contains("decidió")
                    || q.contains("decidio")) {
                return QueryType.COUNT_AND_EXPLAIN;
            }
            return QueryType.COUNT_DOCUMENTS;
        }

        if (q.contains("se habló de") || q.contains("se hablo de") || q.contains("mencion")) {
            if (q.contains("presupuesto") || q.contains("presupuestos")) {
                return QueryType.COUNT_DOCUMENTS;
            }
            if (q.contains("en alguna reunión")
                    || q.contains("en alguna reunion")
                    || q.contains("en alguna acta")
                    || q.contains("en algún acta")
                    || q.contains("en algun acta")) {
                return QueryType.COUNT_DOCUMENTS;
            }
        }

        return classified;
    }

    private static boolean hasDated(String q) {
        return q.matches(".*\\b\\d{4}-\\d{2}-\\d{2}\\b.*")
                || q.matches(".*\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}\\b.*")
                || q.matches(".*\\b\\d{1,2}\\s+de\\s+\\p{L}+\\s+de\\s+\\d{4}\\b.*");
    }
}
