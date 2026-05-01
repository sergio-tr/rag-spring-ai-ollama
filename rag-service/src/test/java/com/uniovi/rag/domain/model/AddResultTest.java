package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AddResultTest {

    @Test
    void enumValues() {
        assertEquals(AddResult.ADDED, AddResult.valueOf("ADDED"));
        assertEquals(AddResult.ALREADY_EXISTS, AddResult.valueOf("ALREADY_EXISTS"));
        assertEquals(2, AddResult.values().length);
    }
}
