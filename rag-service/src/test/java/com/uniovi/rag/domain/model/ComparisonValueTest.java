package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparisonValueTest {

    @Test
    void gettersAndToString() {
        ComparisonValue v = new ComparisonValue(42, ComparisonType.NUMERIC);
        assertEquals(42, v.getValue());
        assertEquals(ComparisonType.NUMERIC, v.getType());
        assertTrue(v.toString().contains("42") && v.toString().contains("NUMERIC"));
    }
}
