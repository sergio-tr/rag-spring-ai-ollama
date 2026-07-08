package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.application.service.runtime.query.ActaFieldAnchorHeuristics;
import com.uniovi.rag.application.service.runtime.query.QueryPlanSlotEnricher;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicEvidenceLevel;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

import java.util.Map;

/** Evaluates deterministic query-shape evidence for tool kind selection. */
public final class DeterministicToolEvidenceEvaluator {

    public record Evaluation(
            DeterministicEvidenceLevel evidenceLevel,
            EnumSet<DeterministicToolKind> matchedKinds,
            boolean heuristicRouteUsed,
            boolean routingOracleUsed,
            boolean toolApplicabilityEligible) {

        public Optional<DeterministicToolKind> singleKind() {
            return matchedKinds.size() == 1 ? Optional.of(matchedKinds.iterator().next()) : Optional.empty();
        }
    }

    private DeterministicToolEvidenceEvaluator() {}

    public static Evaluation evaluate(QueryPlan plan) {
        EnumSet<DeterministicToolKind> matches = EnumSet.noneOf(DeterministicToolKind.class);
        boolean heuristicRouteUsed = false;
        boolean routingOracleUsed = false;

        if (oracleAllowed()) {
            Optional<DeterministicToolKind> oracleKind = oracleKind(plan);
            if (oracleKind.isPresent()) {
                matches.add(oracleKind.get());
                routingOracleUsed = true;
            }
        }

        if (classifierAuthoritative(plan)) {
            addClassifierQueryTypeMatch(plan, matches);
        }
        if (heuristicsAllowed(plan)) {
            heuristicRouteUsed = addHeuristicMatches(plan, matches);
        }

        if (matches.size() > 1 && !routingOracleUsed) {
            disambiguate(plan, matches).ifPresent(
                    kind -> {
                        matches.clear();
                        matches.add(kind);
                    });
        }

        DeterministicEvidenceLevel level = resolveLevel(matches, routingOracleUsed, heuristicRouteUsed, plan);
        boolean eligible =
                level == DeterministicEvidenceLevel.ORACLE
                        || level == DeterministicEvidenceLevel.STRONG
                        || (level == DeterministicEvidenceLevel.WEAK && !matches.isEmpty());
        return new Evaluation(level, matches, heuristicRouteUsed, routingOracleUsed, eligible);
    }

    public static boolean requiredArgumentsPresent(DeterministicToolKind kind, QueryPlan plan) {
        if (kind == DeterministicToolKind.GET_FIELD_TOOL) {
            return hasResolvableGetFieldSlot(plan);
        }
        return true;
    }

    public static Optional<String> classifierVetoReason(QueryPlan plan, Evaluation evaluation) {
        if (plan.classifierStatus() != ClassifierStatus.OK) {
            return Optional.empty();
        }
        Optional<QueryType> classifierType = plan.classifierQueryType();
        if (classifierType.isEmpty()) {
            return Optional.empty();
        }
        QueryType predicted = classifierType.get();
        if (!DeterministicToolApplicability.isApplicableQueryType(predicted)) {
            if (evaluation.evidenceLevel() == DeterministicEvidenceLevel.STRONG
                    || evaluation.evidenceLevel() == DeterministicEvidenceLevel.ORACLE) {
                return Optional.empty();
            }
            return Optional.of(DefaultDeterministicToolResolver.REASON_NON_APPLICABLE_TYPE);
        }
        Optional<DeterministicToolKind> classifierKind = DeterministicToolApplicability.toolKindForQueryType(predicted);
        if (classifierKind.isEmpty()) {
            return Optional.empty();
        }
        if (evaluation.matchedKinds().size() == 1 && evaluation.matchedKinds().contains(classifierKind.get())) {
            return Optional.empty();
        }
        if (evaluation.evidenceLevel() == DeterministicEvidenceLevel.STRONG
                && !evaluation.matchedKinds().isEmpty()
                && !evaluation.matchedKinds().contains(classifierKind.get())) {
            return Optional.of(DefaultDeterministicToolResolver.REASON_CLASSIFIER_HEURISTIC_CONFLICT);
        }
        return Optional.empty();
    }

    private static DeterministicEvidenceLevel resolveLevel(
            EnumSet<DeterministicToolKind> matches,
            boolean routingOracleUsed,
            boolean heuristicRouteUsed,
            QueryPlan plan) {
        if (routingOracleUsed && matches.size() == 1) {
            return DeterministicEvidenceLevel.ORACLE;
        }
        if (matches.size() == 1) {
            return DeterministicEvidenceLevel.STRONG;
        }
        if (matches.size() > 1) {
            return DeterministicEvidenceLevel.WEAK;
        }
        if (heuristicRouteUsed || classifierAuthoritative(plan)) {
            return DeterministicEvidenceLevel.WEAK;
        }
        return DeterministicEvidenceLevel.NONE;
    }

    private static boolean oracleAllowed() {
        return DeterministicToolBenchmarkContext.routingOracleEnabled();
    }

    private static Optional<DeterministicToolKind> oracleKind(QueryPlan plan) {
        return DeterministicToolBenchmarkContext.expectedQueryType()
                .flatMap(DeterministicToolEvidenceEvaluator::parseQueryType)
                .flatMap(DeterministicToolApplicability::toolKindForQueryType);
    }

    private static boolean classifierAuthoritative(QueryPlan plan) {
        return plan.classifierStatus() == ClassifierStatus.OK;
    }

    private static boolean heuristicsAllowed(QueryPlan plan) {
        return switch (plan.classifierStatus()) {
            case OK -> plan.classifierQueryType()
                    .map(DeterministicToolApplicability::isApplicableQueryType)
                    .orElse(true);
            case LOW_CONFIDENCE, INVALID_OUTPUT -> true;
            case UNAVAILABLE, TIMEOUT, INVALID_REQUEST, DISABLED -> true;
            default -> false;
        };
    }

    private static void addClassifierQueryTypeMatch(QueryPlan plan, EnumSet<DeterministicToolKind> matches) {
        Optional<QueryType> classifierType = plan.classifierQueryType();
        if (classifierType.isEmpty()) {
            return;
        }
        Optional<DeterministicToolKind> kind = DeterministicToolApplicability.toolKindForQueryType(classifierType.get());
        if (kind.isEmpty()) {
            return;
        }
        if (kind.get() == DeterministicToolKind.GET_FIELD_TOOL && !hasResolvableGetFieldSlot(plan)) {
            return;
        }
        matches.add(kind.get());
    }

    private static boolean addHeuristicMatches(QueryPlan plan, EnumSet<DeterministicToolKind> matches) {
        int before = matches.size();
        if (matchesCountDocuments(plan)) {
            matches.add(DeterministicToolKind.COUNT_DOCUMENTS_TOOL);
        }
        if (matchesCountAndExplain(plan)) {
            matches.add(DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL);
        }
        if (matchesBoolean(plan)) {
            matches.add(DeterministicToolKind.BOOLEAN_QUERY_TOOL);
        }
        if (matchesGetDuration(plan)) {
            matches.add(DeterministicToolKind.GET_DURATION_TOOL);
        }
        if (matchesFindParagraph(plan)) {
            matches.add(DeterministicToolKind.FIND_PARAGRAPH_TOOL);
        }
        if (matchesFilterAndList(plan)) {
            matches.add(DeterministicToolKind.FILTER_AND_LIST_TOOL);
        }
        if (matchesGetField(plan)) {
            matches.add(DeterministicToolKind.GET_FIELD_TOOL);
        }
        if (matchesSummarizeMeeting(plan)) {
            matches.add(DeterministicToolKind.SUMMARIZE_MEETING_TOOL);
        }
        return matches.size() > before;
    }

    private static Optional<DeterministicToolKind> disambiguate(QueryPlan plan, EnumSet<DeterministicToolKind> matches) {
        String query = queryTextLower(plan);
        if (matches.contains(DeterministicToolKind.GET_DURATION_TOOL) && hasDurationText(query)) {
            return Optional.of(DeterministicToolKind.GET_DURATION_TOOL);
        }
        if (matches.contains(DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL) && hasCountAndExplainText(query)) {
            return Optional.of(DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL);
        }
        if (matches.contains(DeterministicToolKind.FILTER_AND_LIST_TOOL) && hasFilterAndListText(query)) {
            return Optional.of(DeterministicToolKind.FILTER_AND_LIST_TOOL);
        }
        if (matches.contains(DeterministicToolKind.BOOLEAN_QUERY_TOOL) && hasBooleanText(query)) {
            return Optional.of(DeterministicToolKind.BOOLEAN_QUERY_TOOL);
        }
        if (matches.contains(DeterministicToolKind.COUNT_DOCUMENTS_TOOL)
                && (hasCountDocumentsText(query) || hasMeetingMentionCountText(query))) {
            return Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL);
        }
        if (matches.contains(DeterministicToolKind.FIND_PARAGRAPH_TOOL) && hasFindParagraphText(query)) {
            return Optional.of(DeterministicToolKind.FIND_PARAGRAPH_TOOL);
        }
        if (matches.contains(DeterministicToolKind.SUMMARIZE_MEETING_TOOL) && matchesSummarizeMeeting(plan)) {
            return Optional.of(DeterministicToolKind.SUMMARIZE_MEETING_TOOL);
        }
        return Optional.empty();
    }

    private static boolean matchesCountDocuments(QueryPlan plan) {
        if (effectiveIntent(plan) == QueryIntent.COUNT || plan.expectedAnswerShape() == ExpectedAnswerShape.SCALAR_COUNT) {
            return true;
        }
        String query = queryTextLower(plan);
        if (hasDurationText(query) || hasCountAndExplainText(query) || hasBooleanText(query) || hasFilterAndListText(query)) {
            return false;
        }
        return hasCountDocumentsText(query) || hasMeetingMentionCountText(query);
    }

    private static boolean matchesCountAndExplain(QueryPlan plan) {
        if (plan.classifierQueryType().filter(qt -> qt == QueryType.COUNT_AND_EXPLAIN).isPresent()) {
            return true;
        }
        if (effectiveIntent(plan) == QueryIntent.COUNT
                && "true".equalsIgnoreCase(plan.slots().getOrDefault("explain", ""))) {
            return true;
        }
        if (rewriteSlots(plan).getOrDefault("explain", "").equalsIgnoreCase("true")) {
            return true;
        }
        return hasCountAndExplainText(queryTextLower(plan));
    }

    private static boolean matchesBoolean(QueryPlan plan) {
        if (effectiveIntent(plan) == QueryIntent.BOOLEAN_CHECK
                || plan.expectedAnswerShape() == ExpectedAnswerShape.SCALAR_BOOLEAN) {
            return true;
        }
        return hasBooleanText(queryTextLower(plan));
    }

    private static boolean matchesGetDuration(QueryPlan plan) {
        if (plan.classifierQueryType().filter(qt -> qt == QueryType.GET_DURATION).isPresent()) {
            return true;
        }
        String query = queryTextLower(plan);
        if (!hasDurationText(query)) {
            return false;
        }
        if (effectiveIntent(plan) == QueryIntent.FIND && plan.expectedAnswerShape() == ExpectedAnswerShape.PARAGRAPH) {
            return true;
        }
        return plan.queryIntent() == QueryIntent.UNKNOWN
                || plan.expectedAnswerShape() == ExpectedAnswerShape.UNKNOWN
                || effectiveIntent(plan) == QueryIntent.FIND;
    }

    private static boolean matchesFindParagraph(QueryPlan plan) {
        if (matchesGetDuration(plan)) {
            return false;
        }
        if (effectiveIntent(plan) == QueryIntent.FIND && plan.expectedAnswerShape() == ExpectedAnswerShape.PARAGRAPH) {
            return true;
        }
        String query = queryTextLower(plan);
        if (hasCountAndExplainText(query) || hasBooleanText(query) || hasFilterAndListText(query)) {
            return false;
        }
        if (hasCountDocumentsText(query) || hasMeetingMentionCountText(query)) {
            return false;
        }
        return hasFindParagraphText(query);
    }

    private static boolean matchesFilterAndList(QueryPlan plan) {
        if (plan.classifierQueryType().filter(qt -> qt == QueryType.FILTER_AND_LIST).isPresent()) {
            return true;
        }
        if (effectiveIntent(plan) == QueryIntent.LIST || plan.expectedAnswerShape() == ExpectedAnswerShape.LIST) {
            return true;
        }
        return hasFilterAndListText(queryTextLower(plan));
    }

    private static boolean matchesGetField(QueryPlan plan) {
        String query = queryTextLower(plan);
        if (hasCountDocumentsText(query) || hasMeetingMentionCountText(query)) {
            return false;
        }
        boolean shapeOk =
                plan.classifierQueryType().filter(qt -> qt == QueryType.GET_FIELD).isPresent()
                        || effectiveIntent(plan) == QueryIntent.EXTRACT_FIELD
                        || plan.expectedAnswerShape() == ExpectedAnswerShape.FIELD_VALUE;
        if (!shapeOk) {
            return false;
        }
        return hasResolvableGetFieldSlot(plan);
    }

    private static boolean matchesSummarizeMeeting(QueryPlan plan) {
        if (plan.classifierQueryType().filter(qt -> qt == QueryType.SUMMARIZE_MEETING).isEmpty()) {
            return false;
        }
        String query = queryTextLower(plan);
        return query.contains("resume") || query.contains("resum") || query.contains("summar");
    }

    private static boolean hasResolvableGetFieldSlot(QueryPlan plan) {
        String field = plan.slots().get("field");
        if (field == null || field.isBlank()) {
            field = rewriteSlots(plan).get("field");
        }
        if (field == null || field.isBlank()) {
            field = QueryPlanSlotEnricher.inferFieldSlot(plan.normalizedQueryText()).orElse(null);
        }
        if (field == null || field.isBlank()) {
            return false;
        }
        if (ActaFieldAnchorHeuristics.isAttendeeScopedField(field)
                && !ActaFieldAnchorHeuristics.hasExplicitDateInPlan(plan)) {
            return false;
        }
        return true;
    }

    private static QueryIntent effectiveIntent(QueryPlan plan) {
        if (plan.queryIntent() != QueryIntent.UNKNOWN) {
            return plan.queryIntent();
        }
        return plan.structuredRewriteResult()
                .targetAction()
                .flatMap(DeterministicToolEvidenceEvaluator::parseIntent)
                .orElse(QueryIntent.UNKNOWN);
    }

    private static Map<String, String> rewriteSlots(QueryPlan plan) {
        return plan.structuredRewriteResult().slotFilling();
    }

    private static Optional<QueryIntent> parseIntent(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(QueryIntent.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String queryTextLower(QueryPlan plan) {
        String rewritten = plan.rewrittenQueryText() == null ? "" : plan.rewrittenQueryText();
        String normalized = plan.normalizedQueryText() == null ? "" : plan.normalizedQueryText();
        return (rewritten + " " + normalized).toLowerCase(Locale.ROOT);
    }

    private static boolean hasDurationText(String query) {
        return query.contains("duración")
                || query.contains("duracion")
                || query.contains("duration")
                || query.contains("duró")
                || query.contains("duro")
                || query.contains("cuánto duró")
                || query.contains("cuanto duro")
                || query.contains("cuánto duro")
                || query.contains("cuanto duró");
    }

    private static boolean hasCountAndExplainText(String query) {
        boolean countCue =
                query.contains("cuántas")
                        || query.contains("cuantas")
                        || query.contains("cuántos")
                        || query.contains("cuantos")
                        || query.contains("en cuántas")
                        || query.contains("en cuantas")
                        || query.contains("número de")
                        || query.contains("numero de")
                        || query.contains("cuántas veces")
                        || query.contains("cuantas veces");
        boolean explainCue =
                query.contains("qué se")
                        || query.contains("que se")
                        || query.contains("y qué")
                        || query.contains("y que")
                        || query.contains("contexto")
                        || query.contains("decidió")
                        || query.contains("decidio");
        if (countCue && explainCue) {
            return true;
        }
        if (query.contains("cuándo se trató") || query.contains("cuando se trato")) {
            return query.contains("qué") || query.contains("que");
        }
        if (query.contains("en qué reuniones") || query.contains("en que reuniones")) {
            return query.contains("decidió") || query.contains("decidio") || query.contains("qué se");
        }
        return false;
    }

    private static boolean hasFilterAndListText(String query) {
        if ((query.contains("fechas") || query.contains("dates"))
                && query.contains("actas")
                && (query.contains("terminaron")
                        || query.contains("termino")
                        || query.contains("finaliz"))
                && (query.contains("tarde")
                        || query.contains("mas tarde")
                        || query.contains("más tarde")
                        || query.contains("later"))) {
            return true;
        }
        boolean listSubject =
                query.contains("qué actas")
                        || query.contains("que actas")
                        || query.contains("qué reuniones")
                        || query.contains("que reuniones")
                        || query.contains("dime qué actas")
                        || query.contains("dime que actas")
                        || query.contains("dime las actas");
        if (!listSubject) {
            return false;
        }
        if (query.contains(" y ")) {
            return true;
        }
        if (query.contains("tienen")
                || query.contains("tratan")
                || query.contains("celebradas")
                || query.contains("duraron")
                || query.contains("al menos")) {
            return true;
        }
        return (query.contains("mencionan") || query.contains("comentan"))
                && (query.contains("cámara")
                        || query.contains("camara")
                        || query.contains("videovigilancia")
                        || query.contains("ascensor")
                        || query.contains("elevator")
                        || query.contains("convivencia")
                        || query.contains("seguridad")
                        || query.contains("problemas"));
    }

    private static boolean hasBooleanText(String query) {
        return query.contains("confirma si")
                || query.contains("verifica si")
                || query.contains("comprueba si")
                || query.contains("aparece en el acta")
                || query.contains("aparece en la acta")
                || query.contains("figura en el acta")
                || query.contains("figura en la acta");
    }

    private static boolean hasCountDocumentsText(String query) {
        if (hasDurationText(query)) {
            return false;
        }
        return query.contains("cuántas")
                || query.contains("cuantas")
                || query.contains("cuántos")
                || query.contains("cuantos")
                || query.contains("número de")
                || query.contains("numero de")
                || query.contains("en cuántas")
                || query.contains("en cuantas")
                || (query.contains("cuánto") && !query.contains("dur"));
    }

    private static boolean hasMeetingMentionCountText(String query) {
        boolean mention =
                query.contains("se habló de")
                        || query.contains("se hablo de")
                        || query.contains("se habló sobre")
                        || query.contains("se hablo sobre")
                        || query.contains("se mencionó")
                        || query.contains("se menciono");
        boolean scope =
                query.contains("en alguna reunión")
                        || query.contains("en alguna reunion")
                        || query.contains("en alguna acta")
                        || query.contains("en algún acta")
                        || query.contains("en algun acta");
        return mention && scope;
    }

    private static boolean hasFindParagraphText(String query) {
        return query.contains("qué se dijo")
                || query.contains("que se dijo")
                || query.contains("qué se comentó")
                || query.contains("que se comento")
                || query.contains("qué se comentó")
                || query.contains("qué se mencionó")
                || query.contains("que se menciono")
                || query.contains("qué se habló")
                || query.contains("que se hablo")
                || query.contains("en relación a")
                || query.contains("en relacion a")
                || query.contains("respecto a");
    }

    private static Optional<QueryType> parseQueryType(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(QueryType.valueOf(raw.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
