package com.uniovi.rag.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FieldResultTest {

    @Test
    void gettersAndToString() {
        FieldResult r = new FieldResult("min-1", "2025-01-15", "Sala A", "president", "Juan García");
        assertEquals("min-1", r.getMinuteId());
        assertEquals("2025-01-15", r.getDate());
        assertEquals("president", r.getFieldName());
        assertEquals("Juan García", r.getFieldValue());
        assertTrue(r.getIdentifier().contains("min-1"));
        assertTrue(r.toString().contains("president") && r.toString().contains("Juan García"));
    }
}
