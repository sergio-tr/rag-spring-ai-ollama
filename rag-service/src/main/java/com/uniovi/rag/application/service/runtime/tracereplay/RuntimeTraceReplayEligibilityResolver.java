package com.uniovi.rag.application.service.runtime.tracereplay;

import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayDecision;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.infrastructure.persistence.mapper.RuntimeExecutionTraceEntityMapper;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides whether a persisted trace is eligible for P18 replay (strict matrix).
 */
@Service
public class RuntimeTraceReplayEligibilityResolver {

    public RuntimeTraceReplayEligibility resolve(RuntimeExecutionTraceDetailDto trace) {
        if (trace == null) {
            return new RuntimeTraceReplayEligibility(
                    RuntimeTraceReplayDecision.reject(
                            RuntimeTraceReplayOutcome.NOT_ATTEMPTED, Optional.of("trace_null")),
                    Optional.empty());
        }
        if (trace.conversationId() == null || trace.messageId() == null) {
            return reject(RuntimeTraceReplayOutcome.UNSUPPORTED_MISSING_LINKAGE, "missing_conversation_or_message_id");
        }
        if (trace.resolvedConfigSnapshotId() == null) {
            return reject(RuntimeTraceReplayOutcome.UNSUPPORTED_MISSING_RESOLVED_CONFIG_SNAPSHOT_ID, "missing_snapshot_id");
        }
        if (trace.schemaVersion() != RuntimeExecutionTraceEntityMapper.TRACE_SCHEMA_VERSION) {
            return reject(RuntimeTraceReplayOutcome.UNSUPPORTED_TRACE_SCHEMA_VERSION, "schema_version=" + trace.schemaVersion());
        }
        if (trace.judgeAttempted()) {
            return reject(RuntimeTraceReplayOutcome.UNSUPPORTED_JUDGE_AFFECTED, "judgeAttempted=true");
        }
        if (!clarificationSupported(trace.clarificationOutcome())) {
            return reject(
                    RuntimeTraceReplayOutcome.UNSUPPORTED_CLARIFICATION_DEPENDENT,
                    "clarificationOutcome=" + trace.clarificationOutcome());
        }

        AdaptiveRouteKind route;
        try {
            route = AdaptiveRouteKind.valueOf(trimToEmpty(trace.routingRouteKind()));
        } catch (IllegalArgumentException e) {
            return reject(RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY, "routingRouteKind_unparseable");
        }

        if (route == AdaptiveRouteKind.FUNCTION_CALLING_ROUTE || route == AdaptiveRouteKind.ADVISOR_ROUTE) {
            return reject(RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY, "route=" + route);
        }

        Map<String, Object> json = trace.executionTraceJson();
        List<UUID> knowledgeIds = PersistedRuntimeTraceJson.readUsedKnowledgeSnapshotIds(json);
        String toolKind = PersistedRuntimeTraceJson.readDeterministicToolKind(json);
        String wf = trimToEmpty(trace.workflowName());

        switch (route) {
            case DIRECT_WORKFLOW_ROUTE -> {
                if (wf.isEmpty()) {
                    return reject(RuntimeTraceReplayOutcome.UNSUPPORTED_MISSING_WORKFLOW_NAME, "workflowName_missing");
                }
                if (workflowRequiresKnowledgeSnapshots(wf) && knowledgeIds.isEmpty()) {
                    return reject(RuntimeTraceReplayOutcome.UNSUPPORTED_MISSING_KNOWLEDGE_BINDING, "usedKnowledgeSnapshotIds_missing");
                }
                return ok(new PinnedReplayExecutionSpec(route, wf, knowledgeIds, ""));
            }
            case RETRIEVAL_WORKFLOW_ROUTE -> {
                if (wf.isEmpty()) {
                    return reject(RuntimeTraceReplayOutcome.UNSUPPORTED_MISSING_WORKFLOW_NAME, "workflowName_missing");
                }
                if (knowledgeIds.isEmpty()) {
                    return reject(RuntimeTraceReplayOutcome.UNSUPPORTED_MISSING_KNOWLEDGE_BINDING, "usedKnowledgeSnapshotIds_missing");
                }
                return ok(new PinnedReplayExecutionSpec(route, wf, knowledgeIds, ""));
            }
            case DETERMINISTIC_TOOL_ROUTE -> {
                if (toolKind.isEmpty()) {
                    return reject(RuntimeTraceReplayOutcome.UNSUPPORTED_MISSING_TOOL_BINDING, "deterministicToolKind_missing");
                }
                return ok(new PinnedReplayExecutionSpec(route, "", List.of(), toolKind));
            }
            default -> {
                return reject(RuntimeTraceReplayOutcome.UNSUPPORTED_ROUTE_FAMILY, "route=" + route);
            }
        }
    }

    private static boolean clarificationSupported(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            ClarificationOutcome o = ClarificationOutcome.valueOf(raw.trim());
            return o == ClarificationOutcome.NOT_NEEDED || o == ClarificationOutcome.DISABLED_BY_CONFIG;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static RuntimeTraceReplayEligibility reject(RuntimeTraceReplayOutcome outcome, String detail) {
        return new RuntimeTraceReplayEligibility(
                RuntimeTraceReplayDecision.reject(outcome, Optional.ofNullable(detail)), Optional.empty());
    }

    private static RuntimeTraceReplayEligibility ok(PinnedReplayExecutionSpec spec) {
        return new RuntimeTraceReplayEligibility(RuntimeTraceReplayDecision.ok(), Optional.of(spec));
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Same matrix as orchestrated workflow execution: dense / full-corpus workflows require pinned snapshots.
     */
    private static boolean workflowRequiresKnowledgeSnapshots(String workflowName) {
        return "FullCorpusWorkflow".equals(workflowName)
                || "DocumentDenseRagWorkflow".equals(workflowName)
                || "ChunkDenseRagWorkflow".equals(workflowName)
                || "ChunkDenseMetadataWorkflow".equals(workflowName);
    }

    public record RuntimeTraceReplayEligibility(RuntimeTraceReplayDecision decision, Optional<PinnedReplayExecutionSpec> pin) {
        public RuntimeTraceReplayEligibility {
            pin = java.util.Objects.requireNonNull(pin, "pin");
        }
    }
}
