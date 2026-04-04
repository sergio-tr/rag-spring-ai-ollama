package com.uniovi.rag.tool;

import com.uniovi.rag.tool.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultTest {

    @Test
    void recordAccessors() {
        ToolResult tr = new ToolResult("result text", "MyTool");
        assertEquals("result text", tr.result());
        assertEquals("MyTool", tr.source());
    }

    @Test
    void from() {
        ToolResult tr = ToolResult.from("count: 3", ToolResult.class);
        assertEquals("count: 3", tr.result());
        assertEquals("ToolResult", tr.source());
    }
}
