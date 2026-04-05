package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;
import java.util.Map;

/**
 * Final SSE payload before {@code [DONE]}.
 */
public record StreamDoneEvent(
        String answer,
        String queryType,
        boolean usedTool,
        String toolUsed,
        List<Map<String, Object>> sources,
        List<Map<String, Object>> pipelineSteps
) {
}
