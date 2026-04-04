package com.uniovi.rag.application.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DraftAndContextTest {

    @Test
    void recordAccessors() {
        DraftAndContext d = new DraftAndContext("draft answer", "context used");
        assertEquals("draft answer", d.draft());
        assertEquals("context used", d.context());
    }
}
