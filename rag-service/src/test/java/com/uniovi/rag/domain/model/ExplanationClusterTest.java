package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExplanationClusterTest {

    @Test
    void representativeAndContent() {
        Explanation e1 = new Explanation("m1", "2025-01-01", "A", "First", 0.5, System.currentTimeMillis());
        Explanation e2 = new Explanation("m2", "2025-01-02", "B", "Second", 0.9, System.currentTimeMillis());
        ExplanationCluster c = new ExplanationCluster(e1);
        c.addExplanation(e2);
        assertEquals(e2, c.getRepresentativeExplanation());
        assertEquals("Second", c.getRepresentativeContent());
    }
}
