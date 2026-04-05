package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicResultTest {

    @Test
    void gettersAndIdentifier() {
        TopicResult r = new TopicResult("min-1", "2025-01-15", "Sala A", "Resumen del tema presupuesto.");
        assertEquals("min-1", r.getMinuteId());
        assertEquals("2025-01-15", r.getDate());
        assertEquals("Resumen del tema presupuesto.", r.getTopicSummary());
        assertTrue(r.getIdentifier().contains("min-1"));
    }
}
