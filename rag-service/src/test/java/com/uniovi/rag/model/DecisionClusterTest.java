package com.uniovi.rag.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DecisionClusterTest {

    @Test
    void representativeIsLongestDecisionText() {
        Decision d1 = new Decision("m1", "2025-01-01", "A", "Short", "TYPE", List.of(), 0L);
        Decision d2 = new Decision("m2", "2025-01-02", "B", "Much longer decision text here", "TYPE", List.of(), 0L);
        DecisionCluster c = new DecisionCluster(d1);
        c.addDecision(d2);
        assertEquals(d2, c.getRepresentativeDecision());
        assertEquals("Much longer decision text here", c.getRepresentativeContent());
    }

    @Test
    void singleDecision() {
        Decision d = new Decision("m", null, null, "Only", "T", List.of(), 0L);
        DecisionCluster c = new DecisionCluster(d);
        assertEquals(d, c.getRepresentativeDecision());
        assertEquals("Only", c.getRepresentativeContent());
    }
}
