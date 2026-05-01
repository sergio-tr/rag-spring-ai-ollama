package com.uniovi.rag.domain.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionCallingDomainTypesTest {

    @Test
    void functionCallingMode_valuesAreStable() {
        assertEquals(FunctionCallingMode.DISABLED, FunctionCallingMode.valueOf("DISABLED"));
        assertEquals(2, FunctionCallingMode.values().length);
    }

    @Test
    void functionCallingOutcome_enumCoversPersistenceSet() {
        assertTrue(FunctionCallingOutcome.EXECUTED_SUCCESS.name().contains("SUCCESS"));
        assertEquals(10, FunctionCallingOutcome.values().length);
    }

    @Test
    void functionCallingDecision_normalizesNullOptionalAndCopiesCollections() {
        FunctionCallingDecision d =
                new FunctionCallingDecision(
                        FunctionCallingMode.ENABLED,
                        FunctionCallingOutcome.NOT_APPLICABLE,
                        false,
                        List.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL),
                        List.of("r"),
                        null,
                        "q",
                        Map.of("k", "v"));
        assertTrue(d.suppressionReason().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> d.reasons().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> d.normalizedInputs().put("a", "b"));
    }

    @Test
    void functionCallingExecutionResult_normalizesOptionalAndCopiesCollections() {
        FunctionCallingExecutionResult r =
                new FunctionCallingExecutionResult(
                        FunctionCallingOutcome.EXECUTED_SUCCESS,
                        true,
                        null,
                        null,
                        Map.of(),
                        List.of(),
                        false,
                        List.of());
        assertTrue(r.selectedToolKind().isEmpty());
        assertEquals("", r.answerText());
        assertTrue(r.normalizedPayload().isEmpty());
        assertTrue(r.traceNotes().isEmpty());
        assertTrue(r.stageTraces().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> r.traceNotes().add("n"));
    }
}
