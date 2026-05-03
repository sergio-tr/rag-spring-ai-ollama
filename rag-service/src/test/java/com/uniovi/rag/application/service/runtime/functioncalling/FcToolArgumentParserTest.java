package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.*;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FcToolArgumentParserTest {

    private static QueryPlan plan(String rewritten, List<String> targetAttrs) {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        StructuredRewriteResult rewrite =
                new StructuredRewriteResult(
                        rewritten,
                        true,
                        List.of("OK"),
                        StructuredRewriteResult.STRATEGY_STRUCTURED_V1,
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Map.of(),
                        List.of());
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "raw",
                rewritten,
                "L",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.EXTRACT_FIELD,
                Map.of(),
                List.of(),
                targetAttrs,
                entities,
                rewrite,
                ExpectedAnswerShape.FIELD_VALUE,
                AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }

    @Test
    void parsesSingleQuery() {
        QueryPlan p = plan("hello", List.of("f"));
        var r = FcToolArgumentParser.parseOrThrow("{\"query\":\"hello\"}", DeterministicToolKind.COUNT_DOCUMENTS_TOOL, p);
        assertEquals("hello", r.query());
        assertNull(r.field());
    }

    @Test
    void rejectsQueryMismatch() {
        QueryPlan p = plan("hello", List.of("f"));
        assertThrows(IllegalArgumentException.class,
                () -> FcToolArgumentParser.parseOrThrow("{\"query\":\"other\"}", DeterministicToolKind.COUNT_DOCUMENTS_TOOL, p));
    }

    @Test
    void parsesGetField() {
        QueryPlan p = plan("hello", List.of("date"));
        var r = FcToolArgumentParser.parseOrThrow(
                "{\"query\":\"hello\",\"field\":\"date\"}", DeterministicToolKind.GET_FIELD_TOOL, p);
        assertEquals("date", r.field());
    }

    @Test
    void rejectsExtraField() {
        QueryPlan p = plan("hello", List.of("date"));
        assertThrows(IllegalArgumentException.class,
                () -> FcToolArgumentParser.parseOrThrow(
                        "{\"query\":\"hello\",\"field\":\"date\",\"x\":1}", DeterministicToolKind.GET_FIELD_TOOL, p));
    }

    @Test
    void countAndExplainUsesSingleQuerySchema() {
        QueryPlan p = plan("q", List.of());
        var r = FcToolArgumentParser.parseOrThrow("{\"query\":\"q\"}", DeterministicToolKind.COUNT_AND_EXPLAIN_TOOL, p);
        assertEquals("q", r.query());
    }
}
