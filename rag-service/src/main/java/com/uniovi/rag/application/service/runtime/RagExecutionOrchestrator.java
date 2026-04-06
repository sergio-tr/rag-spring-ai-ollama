package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RagExecutionOrchestrator {

    private final WorkflowSelector workflowSelector;

    public RagExecutionOrchestrator(WorkflowSelector workflowSelector) {
        this.workflowSelector = workflowSelector;
    }

    public RagExecutionResult execute(ExecutionContext ctx) {
        ExecutionWorkflow workflow = workflowSelector.select(ctx);
        String wname = workflow.workflowName();
        if (requiresKnowledgeSnapshots(wname)
                && ctx.knowledgeSnapshotSelection().orderedSnapshotIds().isEmpty()) {
            throw RagServiceException.knowledgeSnapshotUnavailable();
        }
        RagExecutionContextHolder.set(toLegacy(ctx));
        try {
            RagExecutionResult partial = workflow.execute(ctx);
            ExecutionTrace trace = assembleTrace(ctx, partial, wname);
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

    private static ExecutionTrace assembleTrace(ExecutionContext ctx, RagExecutionResult partial, String workflowName) {
        List<ExecutionStageTrace> all = new ArrayList<>();
        all.addAll(partial.workflowStageTraces());
        return new ExecutionTrace(
                List.copyOf(all),
                workflowName,
                partial.retrievalUsed(),
                partial.metadataUsed(),
                partial.usedKnowledgeSnapshotIds(),
                Optional.empty(),
                Optional.empty(),
                ctx.resolved().compatibility().severity().name());
    }
}
