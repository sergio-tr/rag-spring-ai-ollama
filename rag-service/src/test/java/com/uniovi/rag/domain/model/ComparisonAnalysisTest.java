package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComparisonAnalysisTest {

    @Test
    void getters() {
        ComparisonAnalysis a = new ComparisonAnalysis(10.0, 100.0, 55.0);
        assertEquals(10.0, a.getMin());
        assertEquals(100.0, a.getMax());
        assertEquals(55.0, a.getAvg());
    }
}
