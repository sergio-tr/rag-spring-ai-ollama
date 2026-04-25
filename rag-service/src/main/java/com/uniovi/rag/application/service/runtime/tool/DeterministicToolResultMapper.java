package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.tool.ToolResult;

/**
 * Normalizes raw {@link ToolResult} into orchestrator-facing payloads and validated answer text.
 */
public interface DeterministicToolResultMapper {

    MappedToolOutput map(ToolResult raw, DeterministicToolKind kind);
}
