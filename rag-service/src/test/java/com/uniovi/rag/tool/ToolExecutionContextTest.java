package com.uniovi.rag.tool;

import com.uniovi.rag.domain.model.QueryType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionContextTest {

    @Test
    void of_queryOnly() {
        ToolExecutionContext ctx = ToolExecutionContext.of("query");
        assertEquals("query", ctx.query());
        assertNull(ctx.queryType());
        assertNull(ctx.nerEntities());
    }

    @Test
    void of_queryAndType() {
        ToolExecutionContext ctx = ToolExecutionContext.of("q", QueryType.COUNT_DOCUMENTS);
        assertEquals("q", ctx.query());
        assertEquals(QueryType.COUNT_DOCUMENTS, ctx.queryType());
        assertNull(ctx.nerEntities());
    }

    @Test
    void of_full() throws JSONException {
        JSONObject ner = new JSONObject().put("date", new JSONArray().put("2025-01-01"));
        ToolExecutionContext ctx = ToolExecutionContext.of("q", QueryType.FIND_PARAGRAPH, ner);
        assertEquals("q", ctx.query());
        assertEquals(QueryType.FIND_PARAGRAPH, ctx.queryType());
        assertNotNull(ctx.nerEntities());
        assertTrue(ctx.nerEntities().has("date"));
    }
}
