package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultQueryIntentResolverTest {

    private final DefaultQueryIntentResolver resolver = new DefaultQueryIntentResolver();

    @Test
    void classifierWinsOverRewriteAction() {
        NormalizedQuery nq = new NormalizedQuery("raw", "compare meetings", List.of());
        EntityExtractionResult entities =
                new EntityExtractionResult(List.of(), List.of(), List.of(), List.of(), List.of(),
                        Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        StructuredRewriteResult rewrite =
                new StructuredRewriteResult(
                        "compare meetings",
                        true,
                        List.of("OK"),
                        StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                        List.of(),
                        List.of(),
                        Optional.of("SUMMARIZE"),
                        Map.of(),
                        List.of());

        QueryIntent intent =
                resolver.resolve(
                        nq,
                        Optional.of(QueryType.COMPARE),
                        "COMPARE",
                        ClassifierStatus.OK,
                        rewrite,
                        entities);

        assertEquals(QueryIntent.COMPARE, intent);
    }

    @Test
    void heuristics_coverCountListCompareSummarizeExplainFieldAndBoolean() {
        EntityExtractionResult empty = EntityExtractionResult.emptyWithNote("");
        EntityExtractionResult booleanHint =
                new EntityExtractionResult(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.of("boolean"),
                        Optional.empty(),
                        List.of());

        assertEquals(
                QueryIntent.COUNT,
                resolver.resolve(
                        nq("how many chunks"),
                        Optional.empty(),
                        "",
                        ClassifierStatus.UNAVAILABLE,
                        null,
                        empty));
        assertEquals(
                QueryIntent.LIST,
                resolver.resolve(nq("list items"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
        assertEquals(
                QueryIntent.COMPARE,
                resolver.resolve(nq("compare a and b"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
        assertEquals(
                QueryIntent.SUMMARIZE,
                resolver.resolve(nq("summarize acta"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
        assertEquals(
                QueryIntent.EXPLAIN,
                resolver.resolve(nq("explain why"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
        assertEquals(
                QueryIntent.EXTRACT_FIELD,
                resolver.resolve(nq("show field x"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
        assertEquals(
                QueryIntent.BOOLEAN_CHECK,
                resolver.resolve(
                        nq("is this yes?"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, booleanHint));
    }

    @Test
    void heuristics_unknown_whenNoSignalsMatch() {
        assertEquals(
                QueryIntent.UNKNOWN,
                resolver.resolve(
                        nq("xyz random"),
                        Optional.empty(),
                        "",
                        ClassifierStatus.UNAVAILABLE,
                        null,
                        EntityExtractionResult.emptyWithNote("")));
    }

    @Test
    void rewriteTargetActionUsed_whenClassifierNotAuthoritative() {
        EntityExtractionResult empty = EntityExtractionResult.emptyWithNote("");
        StructuredRewriteResult rewrite =
                new StructuredRewriteResult(
                        "rewritten",
                        true,
                        List.of(),
                        StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                        List.of(),
                        List.of(),
                        Optional.of("SUMMARIZE"),
                        Map.of(),
                        List.of());
        assertEquals(
                QueryIntent.SUMMARIZE,
                resolver.resolve(
                        nq("anything"),
                        Optional.of(QueryType.COMPARE),
                        "COMPARE",
                        ClassifierStatus.UNAVAILABLE,
                        rewrite,
                        empty));
    }

    @Test
    void invalidRewriteTargetAction_fallsBackToHeuristics() {
        StructuredRewriteResult rewrite =
                new StructuredRewriteResult(
                        "x",
                        false,
                        List.of(),
                        StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                        List.of(),
                        List.of(),
                        Optional.of("NOT_AN_ENUM"),
                        Map.of(),
                        List.of());
        assertEquals(
                QueryIntent.LIST,
                resolver.resolve(
                        nq("lista de cosas"),
                        Optional.empty(),
                        "",
                        ClassifierStatus.UNAVAILABLE,
                        rewrite,
                        EntityExtractionResult.emptyWithNote("")));
    }

    @ParameterizedTest
    @EnumSource(QueryType.class)
    void classifierOk_mapsEveryQueryType(QueryType qt) {
        QueryIntent expected =
                switch (qt) {
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
        assertEquals(
                expected,
                resolver.resolve(
                        nq("ignored"),
                        Optional.of(qt),
                        qt.name(),
                        ClassifierStatus.OK,
                        null,
                        EntityExtractionResult.emptyWithNote("")));
    }

    @Test
    void spanishAndMiscHeuristics_coverRemainingBranches() {
        EntityExtractionResult empty = EntityExtractionResult.emptyWithNote("");
        assertEquals(QueryIntent.COUNT, resolver.resolve(nq("cuántos hay"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
        assertEquals(QueryIntent.LIST, resolver.resolve(nq("lista corta"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
        assertEquals(QueryIntent.LIST, resolver.resolve(nq("enumerar todo"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
        assertEquals(QueryIntent.COMPARE, resolver.resolve(nq("comparar opciones"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
        assertEquals(QueryIntent.SUMMARIZE, resolver.resolve(nq("resumen breve"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
        assertEquals(QueryIntent.EXPLAIN, resolver.resolve(nq("explica el motivo"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
        assertEquals(QueryIntent.EXPLAIN, resolver.resolve(nq("por qué falló"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
        assertEquals(QueryIntent.EXPLAIN, resolver.resolve(nq("porque sí"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
        assertEquals(QueryIntent.EXTRACT_FIELD, resolver.resolve(nq("mostrar campo x"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
        assertEquals(QueryIntent.COUNT, resolver.resolve(nq("count items"), Optional.empty(), "", ClassifierStatus.UNAVAILABLE, null, empty));
    }

    @Test
    void spanishYesNoQuestion_withBooleanHint_returnsBooleanCheck() {
        EntityExtractionResult booleanHint =
                new EntityExtractionResult(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.of("boolean"),
                        Optional.empty(),
                        List.of());
        assertEquals(
                QueryIntent.BOOLEAN_CHECK,
                resolver.resolve(
                        nq("¿está listo?"),
                        Optional.empty(),
                        "",
                        ClassifierStatus.UNAVAILABLE,
                        null,
                        booleanHint));
    }

    private static NormalizedQuery nq(String text) {
        return new NormalizedQuery("raw", text, List.of());
    }
}

