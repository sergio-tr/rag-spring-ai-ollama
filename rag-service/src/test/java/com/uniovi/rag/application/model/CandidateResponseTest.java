package com.uniovi.rag.application.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CandidateResponseTest {

    @Test
    void of_singleArg() {
        CandidateResponse r = CandidateResponse.of("text");
        assertEquals("text", r.text());
        assertNull(r.source());
    }

    @Test
    void of_twoArgs() {
        CandidateResponse r = CandidateResponse.of("text", "source");
        assertEquals("text", r.text());
        assertEquals("source", r.source());
    }
}
