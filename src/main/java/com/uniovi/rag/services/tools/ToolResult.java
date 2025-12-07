package com.uniovi.rag.services.tools;


public record ToolResult(String result, String source) {
    public static ToolResult from(String result, Class<?> toolClass) {
        return new ToolResult(result, toolClass.getSimpleName());
    }
}
