package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterResultTest {

    @Test
    void gettersAndIdentifier() {
        FilterResult r = new FilterResult("min-1", "2025-01-15", "Sala A", "Resumen del acta", 85);
        assertEquals("min-1", r.getMinuteId());
        assertEquals("2025-01-15", r.getDate());
        assertEquals("Sala A", r.getPlace());
        assertEquals("Resumen del acta", r.getSummary());
        assertEquals(85, r.getScore());
        assertTrue(r.getIdentifier().contains("min-1"));
        assertTrue(r.toString().contains("85"));
    }
}
