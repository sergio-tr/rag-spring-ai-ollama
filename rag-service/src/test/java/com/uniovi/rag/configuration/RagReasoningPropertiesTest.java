package com.uniovi.rag.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagReasoningPropertiesTest {

    @Test
    void defaults() {
        RagReasoningProperties p = new RagReasoningProperties();
        assertEquals("SIMPLE", p.getStrategy());
        assertEquals(3, p.getMaxSteps());
        assertEquals(512, p.getMaxTokensPerStep());
    }

    @Test
    void setters() {
        RagReasoningProperties p = new RagReasoningProperties();
        p.setStrategy("COT");
        p.setMaxSteps(5);
        p.setMaxTokensPerStep(256);
        assertEquals("COT", p.getStrategy());
        assertEquals(5, p.getMaxSteps());
        assertEquals(256, p.getMaxTokensPerStep());
    }
}
