package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;

import java.util.Locale;
import java.util.Optional;

/**
 * Rule-based adjustments after ML classifier output (Spanish phrasing hints).
 */
public final class ClassifierOverrides {

    /** Sentinel label never returned by {@link #apply(String, QueryType)} rules. */
    static final QueryType RULE_SENTINEL_FOR_RESOLVE = QueryType.DECISION_EXTRACTION;

    private static final QueryType RULE_SENTINEL = RULE_SENTINEL_FOR_RESOLVE;

    private ClassifierOverrides() {}

    /**
     * Returns a rule-matched query type when Spanish phrasing overrides apply, regardless of classifier output.
     */
    public static Optional<QueryType> matchRule(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        QueryType matched = apply(query, RULE_SENTINEL);
        return matched == RULE_SENTINEL ? Optional.empty() : Optional.of(matched);
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

        if (dated
                && (q.contains("quién fue") || q.contains("quien fue") || q.contains("y quién") || q.contains("y quien"))
                && (q.contains("secretari") || q.contains("presidente") || q.contains("presidenta"))) {
            return QueryType.GET_FIELD;
        }

        if ((q.contains("what do you know about") || q.contains("tell me about"))
                && !countCue) {
            return QueryType.FIND_PARAGRAPH;
        }

        if ((q.contains("dates of the minutes") || q.contains("dates of the minute"))
                && (q.contains("elevator") || q.contains("lift"))) {
            return QueryType.FILTER_AND_LIST;
        }

        if (q.contains("hora")
                && (q.contains("empez") || q.contains("comenz") || q.contains("termin") || q.contains("finaliz"))
                && (actaContext || dated || q.contains("esa acta"))) {
            return QueryType.GET_FIELD;
        }

        if (q.contains("qué secciones comparten")
                || q.contains("que secciones comparten")
                || q.contains("qué secciones comunes")
                || q.contains("que secciones comunes")) {
            return QueryType.EXTRACT_ENTITIES;
        }

        if (q.contains("qué actas mencionan") || q.contains("que actas mencionan")) {
            if (q.contains("problemas eléctric")
                    || q.contains("problemas electric")
                    || q.contains("eléctric")
                    || q.contains("electric")) {
                return QueryType.FIND_PARAGRAPH;
            }
            return QueryType.FILTER_AND_LIST;
        }

        if (q.contains("qué actas tienen") || q.contains("que actas tienen")) {
            return QueryType.FILTER_AND_LIST;
        }

        if (q.contains("qué actas tratan") || q.contains("que actas tratan")) {
            return QueryType.FILTER_AND_LIST;
        }

        if (q.contains("qué actas duraron") || q.contains("que actas duraron")) {
            return QueryType.FILTER_AND_LIST;
        }

        if (q.contains("dime qué actas") || q.contains("dime que actas")) {
            return QueryType.FILTER_AND_LIST;
        }

        if ((q.contains("fechas") || q.contains("dates"))
                && (q.contains("terminaron") || q.contains("termino") || q.contains("finaliz"))
                && (q.contains("tarde")
                        || q.contains("más tarde")
                        || q.contains("mas tarde")
                        || q.contains("later"))) {
            return QueryType.FILTER_AND_LIST;
        }

        if (q.contains("dime las actas")
                && (q.contains("mencionan") || q.contains("comentan") || q.contains("comment"))) {
            return QueryType.FILTER_AND_LIST;
        }

        if (q.contains("qué reuniones celebradas") || q.contains("que reuniones celebradas")) {
            return QueryType.FILTER_AND_LIST;
        }

        if (q.contains("en qué actas aparece")
                || q.contains("en que actas aparece")
                || q.contains("en qué actas particip")
                || q.contains("en que actas particip")) {
            return QueryType.FIND_PARAGRAPH;
        }

        if (q.contains("qué papel tuvo") || q.contains("que papel tuvo")) {
            return QueryType.GET_FIELD;
        }

        if ((q.contains("hay actas") || q.contains("existen actas"))
                && (q.contains("menos de") || q.contains("más de") || q.contains("mas de"))) {
            return QueryType.BOOLEAN_QUERY;
        }

        if (q.contains("indícame las secciones")
                || q.contains("indicame las secciones")
                || q.contains("secciones que contiene el acta")) {
            return QueryType.GET_FIELD;
        }

        if (dated
                && (q.contains("cuál fue la duración")
                        || q.contains("cual fue la duracion")
                        || q.contains("duración de la reunión")
                        || q.contains("duracion de la reunion"))) {
            return QueryType.GET_DURATION;
        }

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

        if ((q.contains("resume") || q.contains("resum"))
                && (dated || q.matches(".*\\b(año|ano)\\s+(del\\s+)?\\d{4}\\b.*"))) {
            return QueryType.SUMMARIZE_MEETING;
        }

        if (hasFindParagraphTopicCue(q) && !countCue && !ActaFieldAnchorHeuristics.isCorpusWideExactAttendeeCountListing(q)) {
            return QueryType.FIND_PARAGRAPH;
        }

        if (ActaFieldAnchorHeuristics.isCorpusWideExactAttendeeCountListing(q)
                && (q.contains("decidió")
                        || q.contains("decidio")
                        || q.contains("qué se")
                        || q.contains("que se"))) {
            return QueryType.COUNT_AND_EXPLAIN;
        }

        if (dated
                && (q.contains("cuántas personas")
                        || q.contains("cuantas personas")
                        || q.contains("cuántos asistentes")
                        || q.contains("cuantos asistentes")
                        || q.contains("cuántos participantes")
                        || q.contains("cuantos participantes")
                        || q.contains("quién asistió")
                        || q.contains("quien asistio"))) {
            return QueryType.GET_FIELD;
        }

        if (dated && (q.contains("orden del día") || q.contains("orden del dia")
                || q.contains("puntos del orden") || q.contains("puntos del día")
                || q.contains("puntos del dia"))) {
            return QueryType.GET_FIELD;
        }

        if (!dated
                && (q.contains("puntos del día") || q.contains("puntos del dia")
                        || q.contains("orden del día") || q.contains("orden del dia"))) {
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

        if (q.contains("en qué reuniones se mencion") || q.contains("en que reuniones se mencion")) {
            return QueryType.FILTER_AND_LIST;
        }

        if (q.contains("qué reuniones incluyeron") || q.contains("que reuniones incluyeron")) {
            return QueryType.FILTER_AND_LIST;
        }

        if (q.contains("en qué actas se mencion") || q.contains("en que actas se mencion")) {
            return QueryType.FIND_PARAGRAPH;
        }

        if (actaContext && dated && !countCue) {
            if (q.contains("presidente") || q.contains("presidió") || q.contains("presidio")) {
                return QueryType.GET_FIELD;
            }
            if (q.contains("secretari")) {
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

        if ((q.contains("cuántas actas")
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
                || (q.contains("cuantos") && actaContext))
                && !ActaFieldAnchorHeuristics.isUndatedParticipantCount(q)) {
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

    /**
     * COUNT_DOCUMENTS on an undated participant-count question causes corpus-wide over-answers (FD-CL-01).
     */
    public static boolean shouldRejectCountDocumentsForUndatedParticipantCount(String query, QueryType classified) {
        return classified == QueryType.COUNT_DOCUMENTS
                && ActaFieldAnchorHeuristics.isUndatedParticipantCount(query);
    }

    private static boolean hasDated(String q) {
        return ActaFieldAnchorHeuristics.hasExplicitDateInText(q);
    }

    private static boolean hasFindParagraphTopicCue(String q) {
        return q.contains("qué se dijo")
                || q.contains("que se dijo")
                || q.contains("qué se coment")
                || q.contains("que se coment")
                || q.contains("qué se mencion")
                || q.contains("que se mencion")
                || q.contains("qué se habl")
                || q.contains("que se habl")
                || q.contains("en relación a")
                || q.contains("en relacion a")
                || q.contains("respecto a");
    }
}
