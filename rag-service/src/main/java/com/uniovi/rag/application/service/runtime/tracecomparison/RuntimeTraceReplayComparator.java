package com.uniovi.rag.application.service.runtime.tracecomparison;

import com.uniovi.rag.application.service.runtime.tracereplay.PersistedRuntimeTraceJson;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayAnswerComparisonStatus;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayFieldMismatch;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayMismatchCategory;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pure dimensional comparison between one P16 detail row and one P18 replay {@link ExecutionTrace}.
 * Invoked only after replay outcome {@code REPLAY_SUCCEEDED}.
 */
@Component
public class RuntimeTraceReplayComparator {

    static final int MAX_MISMATCHES = 50;
    static final int MAX_SNIPPET_CHARS = 256;
    static final int MAX_STAGE_NAMES = 200;
    static final int KNOWLEDGE_LIST_EXACT_MAX = 16;

    private static final Set<String> COMPATIBLE_DRIFT_FIELD_PATHS =
            Set.of(
                    "ExecutionTrace.classifierStatus",
                    "ExecutionTrace.classifierLabel",
                    "ExecutionTrace.ambiguityStatus");

    /**
     * Compares bounded dimensions; list size is capped at {@link #MAX_MISMATCHES}.
     */
    public List<RuntimeTraceReplayFieldMismatch> compare(
            RuntimeExecutionTraceDetailDto original, ExecutionTrace replay, Optional<String> replayAnswerText) {
        List<RuntimeTraceReplayFieldMismatch> out = new ArrayList<>();
        compareRouteFamily(original, replay, out);
        compareScalar("workflowName", original.workflowName(), replay.workflowName(), out);
        compareScalar("memoryAttempted", original.memoryAttempted(), replay.memoryAttempted(), out);
        compareScalar("memoryOutcome", original.memoryOutcome(), replay.memoryOutcome(), out);
        compareScalar("routingAttempted", original.routingAttempted(), replay.routingAttempted(), out);
        compareScalar("routingOutcome", original.routingOutcome(), replay.routingOutcome(), out);
        compareScalar("routingFallbackApplied", original.routingFallbackApplied(), replay.routingFallbackApplied(), out);
        compareScalar(
                "routingWorkflowSelectorInvoked",
                original.routingWorkflowSelectorInvoked(),
                replay.routingWorkflowSelectorInvoked(),
                out);
        compareScalar("deterministicToolOutcome", original.deterministicToolOutcome(), replay.deterministicToolOutcome(), out);
        String origDtKind = PersistedRuntimeTraceJson.readDeterministicToolKind(original.executionTraceJson());
        compareScalar("deterministicToolKind", origDtKind, replay.deterministicToolKind(), out);
        boolean origFcAttempted = readBoolean(original.executionTraceJson(), "functionCallingAttempted");
        compareScalar("functionCallingAttempted", origFcAttempted, replay.functionCallingAttempted(), out);
        compareScalar("functionCallingOutcome", original.functionCallingOutcome(), replay.functionCallingOutcome(), out);
        compareScalar("advisorOutcome", original.advisorOutcome(), replay.advisorOutcome(), out);
        compareJudgeSummary(original, replay, out);
        compareScalar("clarificationOutcome", original.clarificationOutcome(), replay.clarificationOutcome(), out);
        compareKnowledgeSnapshotIds(original, replay, out);
        compareStages(original, replay, out);
        compareClassifierFields(original, replay, out);
        compareAnswerDimension(replayAnswerText, out);
        return out;
    }

    public RuntimeTraceReplayAnswerComparisonStatus classifyAnswerStatus(Optional<String> replayAnswerText) {
        if (replayAnswerText.isEmpty() || replayAnswerText.get().isBlank()) {
            return RuntimeTraceReplayAnswerComparisonStatus.REPLAY_ABSENT;
        }
        // P16 detail does not carry original assistant answer text in P19 v1.
        return RuntimeTraceReplayAnswerComparisonStatus.NOT_COMPARABLE_ORIGINAL_ABSENT;
    }

    public boolean isCompatibleMismatchOnly(List<RuntimeTraceReplayFieldMismatch> mismatches) {
        if (mismatches.isEmpty()) {
            return false;
        }
        for (RuntimeTraceReplayFieldMismatch m : mismatches) {
            if (m.category() != RuntimeTraceReplayMismatchCategory.FIELD_VALUE_MISMATCH) {
                return false;
            }
            if (!COMPATIBLE_DRIFT_FIELD_PATHS.contains(m.fieldPath())) {
                return false;
            }
        }
        return true;
    }

    private static void compareRouteFamily(
            RuntimeExecutionTraceDetailDto original, ExecutionTrace replay, List<RuntimeTraceReplayFieldMismatch> out) {
        String o = original.routingRouteKind() != null ? original.routingRouteKind().trim() : "";
        String r = replay.routingRouteKind() != null ? replay.routingRouteKind().trim() : "";
        if (r.isEmpty()) {
            add(
                    out,
                    "routingRouteKind",
                    RuntimeTraceReplayMismatchCategory.STRUCTURAL_MISSING_REPLAY_FIELD,
                    o,
                    r);
            return;
        }
        Optional<AdaptiveRouteKind> origKind = parseRouteKind(o);
        if (origKind.isEmpty()) {
            if (o.isEmpty()) {
                add(out, "routingRouteKind", RuntimeTraceReplayMismatchCategory.FIELD_VALUE_MISMATCH, o, r);
            } else {
                add(out, "routingRouteKind", RuntimeTraceReplayMismatchCategory.STRUCTURAL_UNPARSEABLE_VALUE, o, r);
            }
            return;
        }
        Optional<AdaptiveRouteKind> replayKind = parseRouteKind(r);
        if (replayKind.isEmpty()) {
            add(out, "routingRouteKind", RuntimeTraceReplayMismatchCategory.STRUCTURAL_UNPARSEABLE_VALUE, o, r);
            return;
        }
        if (origKind.get() != replayKind.get()) {
            add(out, "routingRouteKind", RuntimeTraceReplayMismatchCategory.FIELD_VALUE_MISMATCH, o, r);
        }
    }

    private static Optional<AdaptiveRouteKind> parseRouteKind(String s) {
        if (s == null || s.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AdaptiveRouteKind.valueOf(s.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static void compareJudgeSummary(
            RuntimeExecutionTraceDetailDto original, ExecutionTrace replay, List<RuntimeTraceReplayFieldMismatch> out) {
        compareScalar("judgeAttempted", original.judgeAttempted(), replay.judgeAttempted(), out);
        compareScalar("judgeCandidateSource", original.judgeCandidateSource(), replay.judgeCandidateSource(), out);
        compareScalar("judgeFinalOutcome", original.judgeFinalOutcome(), replay.judgeFinalOutcome(), out);
        compareScalar("judgeFinalAnswerFromRetry", original.judgeFinalAnswerFromRetry(), replay.judgeFinalAnswerFromRetry(), out);
    }

    private static void compareClassifierFields(
            RuntimeExecutionTraceDetailDto original, ExecutionTrace replay, List<RuntimeTraceReplayFieldMismatch> out) {
        Map<String, Object> j = original.executionTraceJson();
        compareScalar(
                "ExecutionTrace.classifierStatus",
                readString(j, "classifierStatus"),
                replay.classifierStatus(),
                out);
        compareScalar(
                "ExecutionTrace.classifierLabel",
                readString(j, "classifierLabel"),
                replay.classifierLabel(),
                out);
        compareScalar(
                "ExecutionTrace.ambiguityStatus", readString(j, "ambiguityStatus"), replay.ambiguityStatus(), out);
    }

    private static String readString(Map<String, Object> json, String key) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        Object v = json.get(key);
        return v == null ? "" : v.toString();
    }

    private static void compareKnowledgeSnapshotIds(
            RuntimeExecutionTraceDetailDto original, ExecutionTrace replay, List<RuntimeTraceReplayFieldMismatch> out) {
        List<UUID> origIds = PersistedRuntimeTraceJson.readUsedKnowledgeSnapshotIds(original.executionTraceJson());
        List<UUID> replayIds = replay.usedKnowledgeSnapshotIds();
        int oc = origIds.size();
        int rc = replayIds.size();
        if (oc <= KNOWLEDGE_LIST_EXACT_MAX && rc <= KNOWLEDGE_LIST_EXACT_MAX) {
            if (!origIds.equals(replayIds)) {
                add(
                        out,
                        "usedKnowledgeSnapshotIds",
                        RuntimeTraceReplayMismatchCategory.FIELD_VALUE_MISMATCH,
                        Integer.toString(oc),
                        Integer.toString(rc));
            }
            return;
        }
        if (oc != rc) {
            add(
                    out,
                    "usedKnowledgeSnapshotIds.size",
                    RuntimeTraceReplayMismatchCategory.FIELD_VALUE_MISMATCH,
                    Integer.toString(oc),
                    Integer.toString(rc));
        }
    }

    private static void compareStages(
            RuntimeExecutionTraceDetailDto original, ExecutionTrace replay, List<RuntimeTraceReplayFieldMismatch> out) {
        List<String> origNames = stageNamesFromJson(original.stagesJson());
        List<String> replayNames =
                replay.stages() == null
                        ? List.of()
                        : replay.stages().stream().map(s -> s.stageName()).toList();
        int origFull = origNames.size();
        int replayFull = replayNames.size();
        if (origFull != replayFull) {
            add(
                    out,
                    "stages.count",
                    RuntimeTraceReplayMismatchCategory.STAGE_COUNT_MISMATCH,
                    Integer.toString(origFull),
                    Integer.toString(replayFull));
            return;
        }
        int n = Math.min(MAX_STAGE_NAMES, origFull);
        for (int i = 0; i < n; i++) {
            if (!Objects.equals(origNames.get(i), replayNames.get(i))) {
                add(
                        out,
                        "stages[" + i + "].stageName",
                        RuntimeTraceReplayMismatchCategory.STAGE_SEQUENCE_MISMATCH,
                        origNames.get(i),
                        replayNames.get(i));
                return;
            }
        }
    }

    static List<String> stageNamesFromJson(List<Map<String, Object>> stagesJson) {
        if (stagesJson == null || stagesJson.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Map<String, Object> m : stagesJson) {
            if (m != null && m.size() == 1 && m.containsKey("truncated")) {
                continue;
            }
            if (m == null) {
                continue;
            }
            Object sn = m.get("stageName");
            if (sn != null) {
                names.add(sn.toString());
            }
        }
        return List.copyOf(names);
    }

    private static void compareAnswerDimension(Optional<String> replayAnswerText, List<RuntimeTraceReplayFieldMismatch> out) {
        if (replayAnswerText.isEmpty() || replayAnswerText.get().isBlank()) {
            add(out, "answerText", RuntimeTraceReplayMismatchCategory.ANSWER_TEXT_MISMATCH, "", "");
        }
    }

    private static boolean readBoolean(Map<String, Object> json, String key) {
        if (json == null || json.isEmpty()) {
            return false;
        }
        Object v = json.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return false;
    }

    private static void compareScalar(String path, boolean original, boolean replay, List<RuntimeTraceReplayFieldMismatch> out) {
        if (original != replay) {
            add(out, path, RuntimeTraceReplayMismatchCategory.FIELD_VALUE_MISMATCH, Boolean.toString(original), Boolean.toString(replay));
        }
    }

    private static void compareScalar(String path, String original, String replay, List<RuntimeTraceReplayFieldMismatch> out) {
        String o = original != null ? original : "";
        String r = replay != null ? replay : "";
        if (!o.equals(r)) {
            add(out, path, RuntimeTraceReplayMismatchCategory.FIELD_VALUE_MISMATCH, o, r);
        }
    }

    private static void add(
            List<RuntimeTraceReplayFieldMismatch> out,
            String path,
            RuntimeTraceReplayMismatchCategory category,
            String originalSnippet,
            String replaySnippet) {
        if (out.size() >= MAX_MISMATCHES) {
            return;
        }
        out.add(
                new RuntimeTraceReplayFieldMismatch(
                        path, category, snippet(originalSnippet), snippet(replaySnippet)));
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= MAX_SNIPPET_CHARS) {
            return s;
        }
        return s.substring(0, MAX_SNIPPET_CHARS);
    }

}
