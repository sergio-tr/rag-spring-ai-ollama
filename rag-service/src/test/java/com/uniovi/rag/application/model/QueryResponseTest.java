package com.uniovi.rag.application.model;

import com.uniovi.rag.domain.model.QueryType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueryResponseTest {

    @Test
    void constructorAndGetters() {
        QueryResponse r = new QueryResponse("answer", "tool1", QueryType.COUNT_DOCUMENTS, true, List.of());
        assertEquals("answer", r.getAnswer());
        assertEquals("tool1", r.getToolUsed());
        assertEquals(QueryType.COUNT_DOCUMENTS, r.getQueryType());
        assertTrue(r.isUsedTool());
        assertTrue(r.getSources().isEmpty());
    }

    @Test
    void fromTool() {
        QueryResponse r = QueryResponse.fromTool("count is 5", "CountDocumentsTool", QueryType.COUNT_DOCUMENTS);
        assertEquals("count is 5", r.getAnswer());
        assertEquals("CountDocumentsTool", r.getToolUsed());
        assertEquals(QueryType.COUNT_DOCUMENTS, r.getQueryType());
        assertTrue(r.isUsedTool());
    }

    @Test
    void fromLLM_withQueryType() {
        QueryResponse r = QueryResponse.fromLLM("direct answer", QueryType.FIND_PARAGRAPH);
        assertEquals("direct answer", r.getAnswer());
        assertNull(r.getToolUsed());
        assertEquals(QueryType.FIND_PARAGRAPH, r.getQueryType());
        assertFalse(r.isUsedTool());
    }

    @Test
    void fromLLM_withoutQueryType() {
        QueryResponse r = QueryResponse.fromLLM("direct answer");
        assertEquals("direct answer", r.getAnswer());
        assertNull(r.getToolUsed());
        assertNull(r.getQueryType());
        assertFalse(r.isUsedTool());
    }
}
