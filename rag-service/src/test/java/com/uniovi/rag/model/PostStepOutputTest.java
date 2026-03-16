package com.uniovi.rag.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PostStepOutputTest {

    @Test
    void recordAccessors() {
        PostStepOutput o = new PostStepOutput("text", true);
        assertEquals("text", o.verifiedOrRefinedText());
        assertTrue(o.verified());
    }

    @Test
    void verified() {
        PostStepOutput o = PostStepOutput.verified("answer");
        assertEquals("answer", o.verifiedOrRefinedText());
        assertTrue(o.verified());
    }

    @Test
    void refined() {
        PostStepOutput o = PostStepOutput.refined("refined");
        assertEquals("refined", o.verifiedOrRefinedText());
        assertFalse(o.verified());
    }
}
