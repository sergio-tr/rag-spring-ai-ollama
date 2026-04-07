package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.service.runtime.query.QueryUnderstandingPipeline;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class RagExecutionOrchestrator {

    private final WorkflowSelector workflowSelector;
    private final QueryUnderstandingPipeline queryUnderstandingPipeline;
    private final ExecutionContextFactory executionContextFactory;

    public RagExecutionOrchestrator(
            WorkflowSelector workflowSelector,
            QueryUnderstandingPipeline queryUnderstandingPipeline,
            ExecutionContextFactory executionContextFactory) {
        this.workflowSelector = workflowSelector;
        this.queryUnderstandingPipeline = queryUnderstandingPipeline;
        this.executionContextFactory = executionContextFactory;
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
        RagExecutionContextHolder.set(toLegacy(withPlan));
        try {
            RagExecutionResult partial = workflow.execute(withPlan);
            ExecutionTrace trace = assembleTrace(withPlan, partial, wname, quStages);
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
            List<ExecutionStageTrace> quStages) {
        List<ExecutionStageTrace> all = new ArrayList<>();
        all.addAll(quStages);
        all.addAll(partial.workflowStageTraces());
        QueryPlan qp = ctx.queryPlan().orElse(null);
        return new ExecutionTrace(
                List.copyOf(all),
                workflowName,
                partial.retrievalUsed(),
                partial.metadataUsed(),
                partial.usedKnowledgeSnapshotIds(),
                Optional.empty(),
                Optional.empty(),
                qp != null ? qp.queryPlanVersion() : "",
                qp != null ? qp.classifierStatus().name() : "",
                qp != null ? qp.classifierLabel() : "",
                qp != null ? qp.expectedAnswerShape().name() : "",
                qp != null ? qp.ambiguityAssessment().status().name() : "",
                ctx.resolved().compatibility().severity().name());
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
