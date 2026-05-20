package com.uniovi.rag.application.service.runtime.query.guard;

import java.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link QueryDateExtractor} (regex + NER paths) to raise JaCoCo line coverage on the guard package.
 */
class QueryDateExtractorTest {

    private QueryDateExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new QueryDateExtractor();
    }

    @Test
    void extractNormalizedDate_nullOrBlank_returnsNull() {
        assertNull(extractor.extractNormalizedDate(null, null));
        assertNull(extractor.extractNormalizedDate("   ", null));
    }

    @Test
    void extractNormalizedDate_isoInQuery_returnsIso() {
        assertEquals("2026-03-28", extractor.extractNormalizedDate("Meeting on 2026-03-28 please", null));
    }

    @Test
    void extractNormalizedDate_slashFormat_returnsIso() {
        assertEquals("2026-03-28", extractor.extractNormalizedDate("Date 28/03/2026", null));
    }

    @Test
    void extractNormalizedDate_dashFormat_returnsIso() {
        assertEquals("2026-03-28", extractor.extractNormalizedDate("28-03-2026", null));
    }

    @Test
    void extractNormalizedDate_spanishLongForm_returnsIso() {
        String q = "Reunión el 25 de febrero de 2026 en el salón";
        assertEquals("2026-02-25", extractor.extractNormalizedDate(q, null));
    }

    @Test
    void extractNormalizedDate_spanishWithDeWords_returnsIso() {
        String q = "Acta 15 de marzo de 2025";
        assertEquals("2025-03-15", extractor.extractNormalizedDate(q, null));
    }

    @Test
    void extractNormalizedDate_prefersNerDateArray() {
        JSONObject ner = new JSONObject();
        ner.put("date", new JSONArray().put("2027-01-10"));
        assertEquals("2027-01-10", extractor.extractNormalizedDate("no iso in text", ner));
    }

    @Test
    void parseToLocalDate_delegatesToFlexibleParser() {
        LocalDate d = extractor.parseToLocalDate("2025-12-01");
        assertEquals(LocalDate.of(2025, 12, 1), d);
    }

    @Test
    void parseToLocalDate_invalid_returnsNull() {
        assertNull(extractor.parseToLocalDate("not a date"));
    }
}
