package com.uniovi.rag.application.result.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DraftAndContextTest {

    @Test
    void recordAccessors() {
        DraftAndContext d = new DraftAndContext("draft answer", "context used");
        assertEquals("draft answer", d.draft());
        assertEquals("context used", d.context());
    }
}
