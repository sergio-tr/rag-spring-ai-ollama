package com.uniovi.rag.domain.runtime.tool;

/**
 * Whether deterministic tools are allowed for the current request (from resolved {@code RagConfig}).
 */
public enum ToolExecutionMode {
    DISABLED,
    ENABLED
}
