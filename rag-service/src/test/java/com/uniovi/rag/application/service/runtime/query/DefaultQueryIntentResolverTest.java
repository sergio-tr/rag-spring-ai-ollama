package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.*;
import org.junit.jupiter.api.Test;

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

    private static NormalizedQuery nq(String text) {
        return new NormalizedQuery("raw", text, List.of());
    }
}

