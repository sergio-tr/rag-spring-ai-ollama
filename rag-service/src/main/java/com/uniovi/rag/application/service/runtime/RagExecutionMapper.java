package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;

public final class RagExecutionMapper {

    private RagExecutionMapper() {
    }

    public static QueryResponse toQueryResponse(RagExecutionResult result) {
        QueryType qt = result.queryTypeForLegacy();
        if (result.usedTool()) {
            return QueryResponse.fromToolWithSources(
                    result.answerText(),
                    result.toolUsedLabel() != null ? result.toolUsedLabel() : "tool",
                    qt,
                    result.responseSources());
        }
        return QueryResponse.fromLLMWithSources(result.answerText(), qt, result.responseSources());
    }
}
