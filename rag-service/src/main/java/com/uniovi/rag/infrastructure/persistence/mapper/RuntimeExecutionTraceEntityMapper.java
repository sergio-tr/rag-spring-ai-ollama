package com.uniovi.rag.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.infrastructure.persistence.jpa.RuntimeExecutionTraceEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Single owner of {@code runtime_execution_trace} JSON column shapes and truncation rules.
 */
@Component
public class RuntimeExecutionTraceEntityMapper {

    public static final int TRACE_SCHEMA_VERSION = 2;

    static final int MAX_STAGE_DETAIL_CHARS = 512;
    static final int MAX_STAGE_COUNT = 200;
    static final int MAX_DETAIL_CHARS = 1024;

    private static final TypeReference<Map<String, Object>> MAP_STRING_OBJECT = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public RuntimeExecutionTraceEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RuntimeExecutionTraceEntity toNewEntity(
            UUID userId,
            UUID projectId,
            Optional<UUID> conversationId,
            Optional<UUID> messageId,
            String correlationId,
            ExecutionTrace trace
    ) {
        RuntimeExecutionTraceEntity e = RuntimeExecutionTraceEntity.newForInsert();
        e.setUserId(userId);
        e.setProjectId(projectId);
        conversationId.ifPresent(e::setConversationId);
        messageId.ifPresent(e::setMessageId);
        e.setCorrelationId(correlationId != null ? correlationId : "");

        e.setResolvedConfigSnapshotId(trace.usedResolvedConfigSnapshotId().orElse(null));
        e.setConfigHash(trace.usedConfigHash().orElse(null));

        e.setWorkflowName(trace.workflowName() != null ? trace.workflowName() : "");

        e.setMemoryAttempted(trace.memoryAttempted());
        e.setMemoryOutcome(nullToEmpty(trace.memoryOutcome()));

        e.setRoutingAttempted(trace.routingAttempted());
        e.setRoutingOutcome(nullToEmpty(trace.routingOutcome()));
        e.setRoutingRouteKind(nullToEmpty(trace.routingRouteKind()));
        e.setRoutingFallbackApplied(trace.routingFallbackApplied());
        e.setRoutingWorkflowSelectorInvoked(trace.routingWorkflowSelectorInvoked());

        e.setDeterministicToolOutcome(nullToEmpty(trace.deterministicToolOutcome()));
        e.setFunctionCallingOutcome(nullToEmpty(trace.functionCallingOutcome()));
        e.setAdvisorOutcome(nullToEmpty(trace.advisorOutcome()));

        e.setJudgeAttempted(trace.judgeAttempted());
        e.setJudgeCandidateSource(nullToEmpty(trace.judgeCandidateSource()));
        e.setJudgeFinalOutcome(nullToEmpty(trace.judgeFinalOutcome()));
        e.setJudgeFinalAnswerFromRetry(trace.judgeFinalAnswerFromRetry());

        e.setClarificationOutcome(nullToEmpty(trace.clarificationOutcome()));

        e.setSchemaVersion(TRACE_SCHEMA_VERSION);

        PersistedStages stages = projectStages(trace.stages());
        Map<String, Object> traceJson = projectTrace(trace, stages.truncated());

        e.setExecutionTraceJsonb(traceJson);
        e.setStagesJsonb(stages.stagesJsonb());
        return e;
    }

    private Map<String, Object> projectTrace(ExecutionTrace trace, boolean stagesTruncated) {
        Map<String, Object> m = new LinkedHashMap<>(toJsonMap(trace));
        m.put("schema_version", TRACE_SCHEMA_VERSION);
        m.put("stages_truncated", stagesTruncated);
        m.put("stages_count_original", trace.stages() != null ? trace.stages().size() : 0);

        m.put("deterministicToolDetail", truncate(nullToEmpty(trace.deterministicToolDetail()), MAX_DETAIL_CHARS));
        m.put("judgeDetail", truncate(nullToEmpty(trace.judgeDetail()), MAX_DETAIL_CHARS));
        return m;
    }

    private PersistedStages projectStages(List<ExecutionStageTrace> stages) {
        List<ExecutionStageTrace> safe = stages == null ? List.of() : stages;
        boolean truncated = safe.size() > MAX_STAGE_COUNT;
        List<ExecutionStageTrace> capped = truncated ? safe.subList(0, MAX_STAGE_COUNT) : safe;

        List<Map<String, Object>> out = new ArrayList<>(capped.size() + 1);
        for (ExecutionStageTrace st : capped) {
            Map<String, Object> m = toJsonMap(st);
            String message = m.get("message") instanceof String s ? s : "";
            m.put("message", truncate(message, MAX_STAGE_DETAIL_CHARS));
            out.add(m);
        }
        out.add(Map.of("truncated", truncated));
        return new PersistedStages(out, truncated);
    }

    private Map<String, Object> toJsonMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return objectMapper.convertValue(value, MAP_STRING_OBJECT);
    }

    private static String truncate(String s, int maxChars) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private record PersistedStages(List<Map<String, Object>> stagesJsonb, boolean truncated) {
    }
}

