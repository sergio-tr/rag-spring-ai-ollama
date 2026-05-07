package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import java.util.Map;

public final class RagExecutionMapper {

    private RagExecutionMapper() {
    }

    public static QueryResponse toQueryResponse(RagExecutionResult result) {
        QueryType qt = result.queryTypeForLegacy();
        Map<String, Object> telemetry = ChatExecutionTelemetryMapper.fromTrace(result.executionTrace());
        if (result.usedTool()) {
            return QueryResponse.fromToolWithSources(
                    result.answerText(),
                    result.toolUsedLabel() != null ? result.toolUsedLabel() : "tool",
                    qt,
                    result.responseSources(),
                    telemetry);
        }
        return QueryResponse.fromLLMWithSources(result.answerText(), qt, result.responseSources(), telemetry);
    }
}
