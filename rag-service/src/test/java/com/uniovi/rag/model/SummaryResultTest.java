package com.uniovi.rag.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SummaryResultTest {

    @Test
    void getters() {
        SummaryResult r = new SummaryResult("min-1", "2025-01-15", "Sala A", "Resumen del acta completo.");
        assertEquals("min-1", r.getMinuteId());
        assertEquals("2025-01-15", r.getDate());
        assertEquals("Sala A", r.getPlace());
        assertEquals("Resumen del acta completo.", r.getSummary());
    }
}
