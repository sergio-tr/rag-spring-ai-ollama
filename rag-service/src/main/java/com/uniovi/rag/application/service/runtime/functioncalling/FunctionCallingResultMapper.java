package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.tool.ToolResult;

import java.util.Map;

/** Maps tool execution output to FC answer text and normalized payload for follow-up prompting. */
public interface FunctionCallingResultMapper {

    Map<String, Object> normalizedPayload(ToolResult raw, DeterministicToolKind kind);

    String stableAnswerText(ToolResult raw, DeterministicToolKind kind);
}
