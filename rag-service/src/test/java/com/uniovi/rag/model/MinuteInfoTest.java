package com.uniovi.rag.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinuteInfoTest {

    @Test
    void constructor_setsAllFields() {
        MinuteInfo info = new MinuteInfo("2025-01-15", 10, 90, 2, 5, 1, "Sala A");
        assertEquals("2025-01-15", info.date());
        assertEquals(10, info.attendees());
        assertEquals(90, info.duration());
        assertEquals(2, info.proposals());
        assertEquals(5, info.agendaItems());
        assertEquals(1, info.questions());
        assertEquals("Sala A", info.location());
    }

    @Test
    void toString_includesKeyFields() {
        MinuteInfo info = new MinuteInfo("2025-02-01", 20, 60, 0, 3, 2, "Oficina");
        String s = info.toString();
        assertTrue(s.contains("2025-02-01"));
        assertTrue(s.contains("20"));
        assertTrue(s.contains("60"));
        assertTrue(s.contains("asistentes"));
        assertTrue(s.contains("minutos"));
        assertTrue(s.contains("Oficina"));
    }
}
