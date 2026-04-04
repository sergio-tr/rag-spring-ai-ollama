package com.uniovi.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParagraphResultTest {

    @Test
    void gettersAndIdentifier() {
        ParagraphResult r = new ParagraphResult("min-1", "2025-01-15", "Sala A", "Párrafo relevante...", 90);
        assertEquals("min-1", r.getMinuteId());
        assertEquals("2025-01-15", r.getDate());
        assertEquals("Párrafo relevante...", r.getParagraph());
        assertEquals(90, r.getScore());
        assertTrue(r.getIdentifier().contains("min-1"));
    }
}
