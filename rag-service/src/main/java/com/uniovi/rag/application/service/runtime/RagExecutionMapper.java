package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.result.chat.QueryResponse;
import com.uniovi.rag.application.service.runtime.memory.ConversationMemoryAnchorMetadata;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RagExecutionMapper {

    private RagExecutionMapper() {
    }

    public static QueryResponse toQueryResponse(RagExecutionResult result) {
        QueryType qt = result.resolvedQueryType();
        Map<String, Object> telemetry = new LinkedHashMap<>(ChatExecutionTelemetryMapper.fromTrace(result.executionTrace()));
        List<Map<String, Object>> sources = result.responseSources();
        ChatExecutionTelemetryMapper.enrichRetrievedIdentifiersFromSources(telemetry, sources);
        ConversationMemoryAnchorMetadata.enrichGroundedAnchor(telemetry, sources);
        if (result.usedTool()) {
            return QueryResponse.fromToolWithSources(
                    result.answerText(),
                    result.toolUsedLabel() != null ? result.toolUsedLabel() : "tool",
                    qt,
                    ChatSourceMapper.fromPersistedMaps(result.responseSources()),
                    telemetry);
        }
        return QueryResponse.fromLLMWithSources(
                result.answerText(), qt, ChatSourceMapper.fromPersistedMaps(result.responseSources()), telemetry);
    }
}
