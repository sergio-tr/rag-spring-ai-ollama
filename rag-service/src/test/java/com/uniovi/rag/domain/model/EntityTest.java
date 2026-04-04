package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityTest {

    @Test
    void gettersAndToString() {
        Entity e = new Entity("Juan Pérez", EntityType.PERSON, EntityRole.PRESIDENT);
        assertEquals("Juan Pérez", e.getName());
        assertEquals(EntityType.PERSON, e.getType());
        assertEquals(EntityRole.PRESIDENT, e.getRole());
        assertTrue(e.toString().contains("Juan Pérez") && e.toString().contains("PRESIDENT"));
    }
}
