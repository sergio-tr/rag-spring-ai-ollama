package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DecisionTest {

    @Test
    void gettersAndIdentifier() {
        long ts = System.currentTimeMillis();
        Decision d = new Decision("min-1", "2025-01-15", "Sala A", "Aprobación del presupuesto", "APROBACIÓN", List.of("Entity1"), ts);
        assertEquals("min-1", d.getMinuteId());
        assertEquals("2025-01-15", d.getDate());
        assertEquals("Sala A", d.getPlace());
        assertEquals("Aprobación del presupuesto", d.getDecisionText());
        assertEquals("APROBACIÓN", d.getDecisionType());
        assertEquals(List.of("Entity1"), d.getKeyEntities());
        assertEquals(ts, d.getTimestamp());
        assertTrue(d.getIdentifier().contains("min-1") && d.getIdentifier().contains("2025-01-15"));
        assertTrue(d.getKeyEntitiesAsString().contains("Entity1"));
        assertTrue(d.getAge() >= 0);
    }

    @Test
    void keyEntitiesEmpty_returnsNone() {
        Decision d = new Decision("m", null, null, "text", "type", List.of(), 0L);
        assertEquals("none", d.getKeyEntitiesAsString());
    }
}
