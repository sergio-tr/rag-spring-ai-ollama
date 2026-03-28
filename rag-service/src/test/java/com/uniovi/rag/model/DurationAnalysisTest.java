package com.uniovi.rag.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DurationAnalysisTest {

    @Test
    void getters() {
        DurationAnalysis a = new DurationAnalysis(30, 120, 75.5, List.of(30, 90, 120));
        assertEquals(30, a.getMinDuration());
        assertEquals(120, a.getMaxDuration());
        assertEquals(75.5, a.getAverageDuration());
        assertEquals(List.of(30, 90, 120), a.getAllDurations());
    }
}
