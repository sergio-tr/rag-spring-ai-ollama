package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RankerResultTest {

    @Test
    void of_threeArgs() {
        RankerResult r = RankerResult.of("chosen", 1, List.of(0.5, 1.0, 0.3));
        assertEquals("chosen", r.chosenText());
        assertEquals(1, r.chosenIndex());
        assertEquals(List.of(0.5, 1.0, 0.3), r.scoresPerCandidate());
    }

    @Test
    void of_twoArgs() {
        RankerResult r = RankerResult.of("chosen", 0);
        assertEquals("chosen", r.chosenText());
        assertEquals(0, r.chosenIndex());
        assertNull(r.scoresPerCandidate());
    }
}
