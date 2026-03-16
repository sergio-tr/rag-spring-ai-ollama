package com.uniovi.rag.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RagRankerPropertiesTest {

    @Test
    void defaults() {
        RagRankerProperties p = new RagRankerProperties();
        assertEquals("LLM_AS_JUDGE", p.getStrategy());
        assertEquals(3, p.getCandidatesCount());
        assertEquals(10, p.getMaxCandidates());
        assertFalse(p.isAlwaysRun());
    }

    @Test
    void setters() {
        RagRankerProperties p = new RagRankerProperties();
        p.setStrategy("FAITHFULNESS");
        p.setCandidatesCount(5);
        p.setMaxCandidates(8);
        p.setAlwaysRun(true);
        assertEquals("FAITHFULNESS", p.getStrategy());
        assertEquals(5, p.getCandidatesCount());
        assertEquals(8, p.getMaxCandidates());
        assertTrue(p.isAlwaysRun());
    }
}
