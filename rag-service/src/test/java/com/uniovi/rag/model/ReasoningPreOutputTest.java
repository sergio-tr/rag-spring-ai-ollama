package com.uniovi.rag.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReasoningPreOutputTest {

    @Test
    void of_singleArg() {
        ReasoningPreOutput o = ReasoningPreOutput.of("plan");
        assertEquals("plan", o.thoughtOrPlan());
        assertNull(o.extraContext());
    }

    @Test
    void of_twoArgs() {
        ReasoningPreOutput o = ReasoningPreOutput.of("thought", "context");
        assertEquals("thought", o.thoughtOrPlan());
        assertEquals("context", o.extraContext());
    }

    @Test
    void of_emptyString() {
        ReasoningPreOutput o = ReasoningPreOutput.of("");
        assertEquals("", o.thoughtOrPlan());
        assertNull(o.extraContext());
    }
}
