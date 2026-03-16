package com.uniovi.rag.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MinuteTest {

    @Test
    void recordCreationAndAccessors() {
        Minute m = new Minute(
                "id-1",
                "acta.pdf",
                "2025-02-24",
                "Sala A",
                "10:00",
                "11:00",
                "President",
                "Secretary",
                List.of("A", "B"),
                2,
                Map.of("1", "Punto 1"),
                List.of("Decisión 1"),
                List.of("Entity1"),
                List.of("Topic1"),
                "Resumen"
        );
        assertEquals("id-1", m.id());
        assertEquals("acta.pdf", m.filename());
        assertEquals("2025-02-24", m.date());
        assertEquals("Sala A", m.place());
        assertEquals("President", m.president());
        assertEquals(2, m.numberOfAttendees());
        assertEquals(List.of("A", "B"), m.attendees());
        assertEquals("Resumen", m.summary());
    }

    @Test
    void minuteWithNullOptionalFields() {
        Minute m = new Minute(
                "id-2",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null
        );
        assertEquals("id-2", m.id());
        assertNull(m.filename());
        assertNull(m.summary());
        assertEquals(0, m.numberOfAttendees());
    }
}
