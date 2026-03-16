package com.uniovi.rag.service.analyser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NERQueryEnricherTest {

    private NERQueryEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new NERQueryEnricher(80, 512);
    }

    @Test
    void buildEnrichedQuery_nullOrEmptyNer_returnsTrimmedQuery() {
        assertEquals("query", enricher.buildEnrichedQueryForRetrieval("query", null));
        assertEquals("query", enricher.buildEnrichedQueryForRetrieval("  query  ", new JSONObject()));
    }

    @Test
    void buildEnrichedQuery_withDate_addsDateToQuery() {
        JSONObject ner = new JSONObject();
        ner.put("date", new JSONArray().put("2025-01-15"));
        String result = enricher.buildEnrichedQueryForRetrieval("reunión", ner);
        assertTrue(result.contains("reunión"));
        assertTrue(result.contains("2025-01-15"));
    }

    @Test
    void constructor_enforcesPositiveLimits() {
        NERQueryEnricher e = new NERQueryEnricher(0, 0);
        String r = e.buildEnrichedQueryForRetrieval("q", null);
        assertEquals("q", r);
    }
}
