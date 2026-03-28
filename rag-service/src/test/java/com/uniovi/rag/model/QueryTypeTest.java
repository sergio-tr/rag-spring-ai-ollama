package com.uniovi.rag.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueryTypeTest {

    @Test
    void valueOf() {
        assertEquals(QueryType.COUNT_DOCUMENTS, QueryType.valueOf("COUNT_DOCUMENTS"));
        assertEquals(QueryType.FIND_PARAGRAPH, QueryType.valueOf("FIND_PARAGRAPH"));
        assertEquals(QueryType.DECISION_EXTRACTION, QueryType.valueOf("DECISION_EXTRACTION"));
    }

    @Test
    void values() {
        QueryType[] values = QueryType.values();
        assertTrue(values.length >= 10);
    }
}
