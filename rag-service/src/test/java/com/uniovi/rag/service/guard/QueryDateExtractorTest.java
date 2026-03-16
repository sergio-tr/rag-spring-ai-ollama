package com.uniovi.rag.service.guard;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class QueryDateExtractorTest {

    private QueryDateExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new QueryDateExtractor();
    }

    @Test
    void extractNormalizedDate_isoInQuery() {
        String result = extractor.extractNormalizedDate("Reunión del 2025-02-24", null);
        assertEquals("2025-02-24", result);
    }

    @Test
    void extractNormalizedDate_slashFormat() {
        String result = extractor.extractNormalizedDate("Decisiones del 25/08/2028", null);
        assertEquals("2028-08-25", result);
    }

    @Test
    void extractNormalizedDate_spanishFormat() {
        String result = extractor.extractNormalizedDate("Acta del 25 de agosto de 2028", null);
        assertEquals("2028-08-25", result);
    }

    @Test
    void extractNormalizedDate_nullQuery_returnsNull() {
        assertNull(extractor.extractNormalizedDate(null, null));
        assertNull(extractor.extractNormalizedDate("", null));
        assertNull(extractor.extractNormalizedDate("   ", null));
    }

    @Test
    void extractNormalizedDate_noDateInQuery_returnsNull() {
        assertNull(extractor.extractNormalizedDate("¿Quién presidió?", null));
    }

    @Test
    void extractNormalizedDate_fromNer() {
        JSONObject ner = new JSONObject();
        ner.put("date", new org.json.JSONArray(List.of("2026-03-15")));
        String result = extractor.extractNormalizedDate("algo", ner);
        assertEquals("2026-03-15", result);
    }

    @Test
    void parseToLocalDate_iso() {
        assertEquals(LocalDate.of(2025, 2, 24), extractor.parseToLocalDate("2025-02-24"));
    }

    @Test
    void parseToLocalDate_slash() {
        assertEquals(LocalDate.of(2028, 8, 25), extractor.parseToLocalDate("25/08/2028"));
    }

    @Test
    void parseToLocalDate_invalid_returnsNull() {
        assertNull(extractor.parseToLocalDate(null));
        assertNull(extractor.parseToLocalDate(""));
        assertNull(extractor.parseToLocalDate("not-a-date"));
        assertNull(extractor.parseToLocalDate("32/13/2025"));
    }
}
