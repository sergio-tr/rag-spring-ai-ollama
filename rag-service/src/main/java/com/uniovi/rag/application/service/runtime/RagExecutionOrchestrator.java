package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.service.runtime.query.QueryUnderstandingPipeline;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolKindMappings;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolStrategy;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class RagExecutionOrchestrator {

    private final WorkflowSelector workflowSelector;
    private final QueryUnderstandingPipeline queryUnderstandingPipeline;
    private final ExecutionContextFactory executionContextFactory;
    private final DeterministicToolStrategy deterministicToolStrategy;

    public RagExecutionOrchestrator(
            WorkflowSelector workflowSelector,
            QueryUnderstandingPipeline queryUnderstandingPipeline,
            ExecutionContextFactory executionContextFactory,
            DeterministicToolStrategy deterministicToolStrategy) {
        this.workflowSelector = workflowSelector;
        this.queryUnderstandingPipeline = queryUnderstandingPipeline;
        this.executionContextFactory = executionContextFactory;
        this.deterministicToolStrategy = deterministicToolStrategy;
    }

    public RagExecutionResult execute(ExecutionContext ctx) {
        long quStart = System.nanoTime();
        QueryPlan plan = queryUnderstandingPipeline.buildPlan(ctx);
        ExecutionContext withPlan = executionContextFactory.attachQueryPlan(ctx, plan);

        List<ExecutionStageTrace> quStages = projectQuStages(plan);
        quStages.add(0, new ExecutionStageTrace(
                "qu_total",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - quStart),
                ExecutionStageOutcome.SUCCESS,
                "qu_status=OK message=QueryUnderstandingPipeline completed"));

        ExecutionWorkflow workflow = workflowSelector.select(withPlan);
        String wname = workflow.workflowName();
        if (requiresKnowledgeSnapshots(wname)
                && withPlan.knowledgeSnapshotSelection().orderedSnapshotIds().isEmpty()) {
            throw RagServiceException.knowledgeSnapshotUnavailable();
        }

        DeterministicToolExecutionResult toolResult =
                deterministicToolStrategy.tryExecute(withPlan, plan, wname);
        List<ExecutionStageTrace> toolStages = projectDeterministicToolStages(toolResult);

        if (toolResult.outcome() == DeterministicToolOutcome.EXECUTED_SUCCESS && toolResult.success()) {
            DeterministicToolKind kind =
                    toolResult.toolKind().orElseThrow(() -> new IllegalStateException("tool kind missing on success"));
            RagExecutionResult partial = new RagExecutionResult(
                    toolResult.answerText(),
                    wname,
                    false,
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    withPlan.knowledgeSnapshotSelection().orderedSnapshotIds(),
                    ExecutionTrace.placeholder(),
                    "deterministic-tool",
                    DeterministicToolKindMappings.toQueryType(kind),
                    true,
                    List.of(),
                    Optional.empty());
            ExecutionTrace trace = assembleTrace(withPlan, partial, wname, quStages, toolStages, toolResult);
            return partial.withFinalTrace(trace);
        }

        RagExecutionContextHolder.set(toLegacy(withPlan));
        try {
            RagExecutionResult partial = workflow.execute(withPlan);
            ExecutionTrace trace = assembleTrace(withPlan, partial, wname, quStages, toolStages, toolResult);
            return partial.withFinalTrace(trace);
        } finally {
            RagExecutionContextHolder.clear();
        }
    }

    private static boolean requiresKnowledgeSnapshots(String workflowName) {
        return "FullCorpusWorkflow".equals(workflowName)
                || "DocumentDenseRagWorkflow".equals(workflowName)
                || "ChunkDenseRagWorkflow".equals(workflowName)
                || "ChunkDenseMetadataWorkflow".equals(workflowName);
    }

    private static RagExecutionContext toLegacy(ExecutionContext ctx) {
        return new RagExecutionContext(
                ctx.conversationId() != null ? ctx.conversationId().toString() : null,
                ctx.userId() != null ? ctx.userId().toString() : null,
                ctx.projectId() != null ? ctx.projectId().toString() : null,
                ctx.resolved().toRagConfig(),
                ctx.documentFilter(),
                ctx.correlationId());
    }

    private static ExecutionTrace assembleTrace(
            ExecutionContext ctx,
            RagExecutionResult partial,
            String workflowName,
            List<ExecutionStageTrace> quStages,
            List<ExecutionStageTrace> toolStages,
            DeterministicToolExecutionResult toolResult) {
        List<ExecutionStageTrace> all = new ArrayList<>();
        all.addAll(quStages);
        all.addAll(toolStages);
        all.addAll(partial.workflowStageTraces());
        QueryPlan qp = ctx.queryPlan().orElse(null);
        String toolOutcome = toolResult.outcome().name();
        String toolKind = toolResult.toolKind().map(Enum::name).orElse("");
        String toolDetail = buildToolDetail(toolResult);
        return new ExecutionTrace(
                List.copyOf(all),
                workflowName,
                partial.retrievalUsed(),
                partial.metadataUsed(),
                partial.usedKnowledgeSnapshotIds(),
                partial.usedResolvedConfigSnapshotId(),
                partial.usedConfigHash(),
                qp != null ? qp.queryPlanVersion() : "",
                qp != null ? qp.classifierStatus().name() : "",
                qp != null ? qp.classifierLabel() : "",
                qp != null ? qp.expectedAnswerShape().name() : "",
                qp != null ? qp.ambiguityAssessment().status().name() : "",
                ctx.resolved().compatibility().severity().name(),
                toolOutcome,
                toolKind,
                toolDetail,
                partial.retrievalDiagnostics());
    }

    private static String buildToolDetail(DeterministicToolExecutionResult toolResult) {
        String notes = toolResult.traceNotes().stream().collect(Collectors.joining(";"));
        if (toolResult.outcome() == DeterministicToolOutcome.EXECUTED_FAILED_INFRA) {
            return "tool_fallback_to_workflow;" + notes;
        }
        return notes;
    }

    private static List<ExecutionStageTrace> projectDeterministicToolStages(DeterministicToolExecutionResult r) {
        List<ExecutionStageTrace> out = new ArrayList<>();
        String msgBase = "outcome=" + r.outcome() + " success=" + r.success();
        String notes = String.join(" | ", r.traceNotes());
        out.add(new ExecutionStageTrace(
                "tool_resolve",
                0L,
                ExecutionStageOutcome.SUCCESS,
                msgBase + " notes=" + notes));

        ExecutionStageOutcome execOutcome;
        if (r.outcome() == DeterministicToolOutcome.EXECUTED_SUCCESS) {
            execOutcome = ExecutionStageOutcome.SUCCESS;
        } else if (r.outcome() == DeterministicToolOutcome.EXECUTED_FAILED_INFRA) {
            execOutcome = ExecutionStageOutcome.FAILED;
        } else {
            execOutcome = ExecutionStageOutcome.SKIPPED;
        }
        out.add(new ExecutionStageTrace("tool_execute", 0L, execOutcome, msgBase));

        ExecutionStageOutcome mapOutcome =
                r.outcome() == DeterministicToolOutcome.EXECUTED_SUCCESS
                        ? ExecutionStageOutcome.SUCCESS
                        : ExecutionStageOutcome.SKIPPED;
        out.add(new ExecutionStageTrace("tool_result_map", 0L, mapOutcome, msgBase));
        return out;
    }

    private static List<ExecutionStageTrace> projectQuStages(QueryPlan plan) {
        List<ExecutionStageTrace> out = new ArrayList<>();
        for (String line : plan.pipelineNotes()) {
            ExecutionStageTrace st = parseStageTraceLine(line);
            if (st != null && isFrozenQuStageName(st.stageName())) {
                out.add(st);
            }
        }
        return out;
    }

    private static boolean isFrozenQuStageName(String name) {
        return "qu_normalize".equals(name)
                || "qu_classify".equals(name)
                || "qu_extract_entities".equals(name)
                || "qu_rewrite".equals(name)
                || "qu_resolve_intent".equals(name)
                || "qu_resolve_answer_shape".equals(name)
                || "qu_assess_ambiguity".equals(name);
    }

    private static ExecutionStageTrace parseStageTraceLine(String line) {
        // Expected format:
        // <stageName> qu_status=<OK|FALLBACK|DISABLED|ERROR> durationMs=<n> message=<text>
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\\s+");
        if (parts.length < 3) {
            return null;
        }
        String stageName = parts[0].trim();
        String quStatus = extractToken(line, "qu_status=");
        String durationRaw = extractToken(line, "durationMs=");
        long durationMs = 0;
        try {
            durationMs = Long.parseLong(durationRaw);
        } catch (Exception ignored) {
            durationMs = 0;
        }
        ExecutionStageOutcome outcome = switch (quStatus) {
            case "OK", "FALLBACK" -> ExecutionStageOutcome.SUCCESS;
            case "DISABLED" -> ExecutionStageOutcome.SKIPPED;
            case "ERROR" -> ExecutionStageOutcome.FAILED;
            default -> ExecutionStageOutcome.SUCCESS;
        };
        return new ExecutionStageTrace(stageName, durationMs, outcome, "qu_status=" + quStatus + " " + extractMessage(line));
    }

    private static String extractToken(String line, String key) {
        int idx = line.indexOf(key);
        if (idx < 0) return "";
        int start = idx + key.length();
        int end = line.indexOf(' ', start);
        return end < 0 ? line.substring(start).trim() : line.substring(start, end).trim();
    }

    private static String extractMessage(String line) {
        int idx = line.indexOf("message=");
        if (idx < 0) return "";
        return "message=" + line.substring(idx + "message=".length()).trim();
    }
}
