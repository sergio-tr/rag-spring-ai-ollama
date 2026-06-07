package com.uniovi.rag.application.result.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
