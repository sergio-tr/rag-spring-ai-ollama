package com.uniovi.rag.application.service.runtime.query.guard;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.service.runtime.query.guard.DateExistenceGuard;
import com.uniovi.rag.application.service.runtime.query.guard.QueryDateExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.tool.ToolResult;
import com.uniovi.rag.application.service.runtime.query.guard.DefaultDateExistenceGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P11 regression: P1 date existence guard – when no document exists for the requested date,
 * guard returns standard "no acta" response without calling the tool.
 */
class DateExistenceGuardTest {

    private ContextRetriever retriever;
    private QueryDateExtractor dateExtractor;
    private DateExistenceGuard guard;

    @BeforeEach
    void setUp() {
        retriever = mock(ContextRetriever.class);
        dateExtractor = new QueryDateExtractor();
        guard = new DefaultDateExistenceGuard(retriever, dateExtractor);
    }

    @Test
    void returnsNoActaWhenRetrievedDocsDoNotMatchRequestedDate() {
        // Document from 2025, query asks for 2028
        Document doc = new Document("content", Map.of("date_iso", "2025-02-24"));
        when(retriever.retrieve(anyString())).thenReturn(List.of(doc));

        Optional<ToolResult> result = guard.checkNoActaForDate(
                "¿Qué decisiones se tomaron el 25/08/2028?", QueryType.DECISION_EXTRACTION, null);

        assertTrue(result.isPresent());
        ToolResult tr = result.get();
        assertTrue(tr.result().contains("No hay ninguna acta") && tr.result().contains("esa fecha"));
        assertTrue(tr.result().contains("decisión") || tr.result().contains("decisión"));
        assertEquals("DateExistenceGuard", tr.source());
    }

    @Test
    void returnsEmptyWhenAtLeastOneDocMatchesRequestedDate() {
        Document doc = new Document("content", Map.of("date_iso", "2028-08-25"));
        when(retriever.retrieve(anyString())).thenReturn(List.of(doc));

        Optional<ToolResult> result = guard.checkNoActaForDate(
                "Decisiones del 25/08/2028", QueryType.DECISION_EXTRACTION, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyForNonDateDependentQueryType() {
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        Optional<ToolResult> result = guard.checkNoActaForDate(
                "¿Quién presidió la reunión?", QueryType.EXTRACT_ENTITIES, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void returnsNoActaWhenFutureDateAndNoDocs_decisionExtraction() {
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        Optional<ToolResult> result = guard.checkNoActaForDate(
                "Decisiones el 2099-12-31", QueryType.DECISION_EXTRACTION, null);
        assertTrue(result.isPresent());
        assertTrue(result.get().result().contains("decisión") || result.get().result().contains("decisiones"));
    }

    @Test
    void returnsNoActaWhenFutureDateAndNoDocs_genericMessageForGetField() {
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        Optional<ToolResult> result = guard.checkNoActaForDate(
                "Campo X el 2099-06-15", QueryType.GET_FIELD, null);
        assertTrue(result.isPresent());
        assertTrue(result.get().result().contains("ninguna acta"));
    }

    @Test
    void usesDateFieldWhenDateIsoMissing() {
        Document doc = new Document("c", Map.of("date", "2025-04-10"));
        when(retriever.retrieve(anyString())).thenReturn(List.of(doc));
        Optional<ToolResult> result = guard.checkNoActaForDate(
                "Decisiones 2025-04-20", QueryType.GET_FIELD, null);
        assertTrue(result.isPresent());
    }

    @Test
    void returnsEmptyWhenNormalizedDateInvalidButDocsExist() {
        QueryDateExtractor mockExtractor = mock(QueryDateExtractor.class);
        when(mockExtractor.extractNormalizedDate(anyString(), any())).thenReturn("not-an-iso-date");
        DateExistenceGuard g = new DefaultDateExistenceGuard(retriever, mockExtractor);
        when(retriever.retrieve(anyString())).thenReturn(List.of(new Document("c", Map.of("date_iso", "2025-01-01"))));

        Optional<ToolResult> result = g.checkNoActaForDate("q", QueryType.GET_FIELD, null);

        assertTrue(result.isEmpty());
        verify(mockExtractor).extractNormalizedDate(anyString(), any());
    }
}
