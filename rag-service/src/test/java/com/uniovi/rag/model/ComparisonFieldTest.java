package com.uniovi.rag.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComparisonFieldTest {

    @Test
    void getters() {
        ComparisonField f = new ComparisonField("duration", ComparisonType.NUMERIC);
        assertEquals("duration", f.getFieldName());
        assertEquals(ComparisonType.NUMERIC, f.getType());
    }
}
