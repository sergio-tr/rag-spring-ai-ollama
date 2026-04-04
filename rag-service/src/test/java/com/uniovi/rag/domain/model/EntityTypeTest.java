package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityTypeTest {

    @Test
    void values() {
        assertTrue(EntityType.values().length >= 2);
        assertEquals(EntityType.PERSON, EntityType.valueOf("PERSON"));
        assertEquals(EntityType.ORGANIZATION, EntityType.valueOf("ORGANIZATION"));
    }
}
