package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CountingAnalysisTest {

    @Test
    void getters() {
        CountingAnalysis a = new CountingAnalysis(5, List.of("2025-01-01"), List.of("Sala A"), List.of("Tema"));
        assertEquals(5, a.getTotalCount());
        assertEquals(List.of("2025-01-01"), a.getDates());
        assertEquals(List.of("Sala A"), a.getPlaces());
        assertEquals(List.of("Tema"), a.getTopics());
        assertNull(a.getAttendeesCounts());
    }

    @Test
    void withAttendeesCounts() {
        CountingAnalysis a = new CountingAnalysis(3, List.of(), List.of(), List.of(), List.of(5, 10, 8));
        assertEquals(3, a.getTotalCount());
        assertEquals(List.of(5, 10, 8), a.getAttendeesCounts());
    }
}
