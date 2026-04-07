package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultQueryIntentResolverTest {

    @Test
    void classifierWinsOverRewriteAction() {
        DefaultQueryIntentResolver resolver = new DefaultQueryIntentResolver();
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
}

