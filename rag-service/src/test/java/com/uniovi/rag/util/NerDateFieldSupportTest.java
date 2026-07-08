package com.uniovi.rag.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NerDateFieldSupportTest {

    @Test
    void readDateStrings_acceptsIsoString() {
        JSONObject ner = new JSONObject().put("date", "2025-02-24");
        assertEquals(List.of("2025-02-24"), NerDateFieldSupport.readDateStrings(ner));
    }

    @Test
    void readDateStrings_acceptsSpanishString() {
        JSONObject ner = new JSONObject().put("date", "24 de febrero de 2025");
        assertEquals(List.of("24 de febrero de 2025"), NerDateFieldSupport.readDateStrings(ner));
    }

    @Test
    void readDateStrings_acceptsArray() {
        JSONObject ner = new JSONObject().put("date", new JSONArray().put("2025-01-01").put("2025-02-01"));
        assertEquals(List.of("2025-01-01", "2025-02-01"), NerDateFieldSupport.readDateStrings(ner));
    }

    @Test
    void readDateStrings_missingOrNull_returnsEmpty() {
        assertTrue(NerDateFieldSupport.readDateStrings(new JSONObject()).isEmpty());
        assertTrue(NerDateFieldSupport.readDateStrings(new JSONObject().put("date", JSONObject.NULL)).isEmpty());
    }

    @Test
    void coerceDateValues_unsupportedShape_returnsEmpty() {
        assertTrue(NerDateFieldSupport.coerceDateValues(new JSONObject()).isEmpty());
    }
}
