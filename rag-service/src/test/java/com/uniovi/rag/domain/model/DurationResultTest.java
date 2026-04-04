package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DurationResultTest {

    @Test
    void gettersAndIdentifier() {
        DurationResult r = new DurationResult("min-1", "2025-01-15", "Sala A", "10:00", "11:30", 90);
        assertEquals("min-1", r.getMinuteId());
        assertEquals("2025-01-15", r.getDate());
        assertEquals("10:00", r.getStartTime());
        assertEquals("11:30", r.getEndTime());
        assertEquals(90, r.getDurationMinutes());
        assertTrue(r.getIdentifier().contains("min-1"));
        assertTrue(r.toString().contains("90"));
    }
}
