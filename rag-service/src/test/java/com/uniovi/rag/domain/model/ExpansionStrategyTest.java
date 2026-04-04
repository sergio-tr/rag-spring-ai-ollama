package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExpansionStrategyTest {

    @Test
    void valueOf() {
        assertEquals(ExpansionStrategy.COT, ExpansionStrategy.valueOf("COT"));
        assertTrue(ExpansionStrategy.values().length >= 1);
    }
}
