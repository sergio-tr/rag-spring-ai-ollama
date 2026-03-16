package com.uniovi.rag.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityRoleTest {

    @Test
    void values() {
        assertEquals(EntityRole.PRESIDENT, EntityRole.valueOf("PRESIDENT"));
        assertEquals(EntityRole.SECRETARY, EntityRole.valueOf("SECRETARY"));
        assertTrue(EntityRole.values().length >= 2);
    }
}
