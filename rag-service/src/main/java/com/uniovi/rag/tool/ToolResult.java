package com.uniovi.rag.tool;


public record ToolResult(String result, String source) {
    public static ToolResult from(String result, Class<?> toolClass) {
        return new ToolResult(result, toolClass.getSimpleName());
    }
}
