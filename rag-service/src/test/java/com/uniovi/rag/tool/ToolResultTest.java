package com.uniovi.rag.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolResultTest {

    @Test
    void from_setsSourceToSimpleClassName() {
        ToolResult r = ToolResult.from("out", ToolResult.class);
        assertEquals("out", r.result());
        assertEquals("ToolResult", r.source());
    }
}
