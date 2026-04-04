package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComparisonTypeTest {

    @Test
    void valueOf() {
        assertEquals(ComparisonType.NUMERIC, ComparisonType.valueOf("NUMERIC"));
        assertTrue(ComparisonType.values().length >= 1);
    }
}
