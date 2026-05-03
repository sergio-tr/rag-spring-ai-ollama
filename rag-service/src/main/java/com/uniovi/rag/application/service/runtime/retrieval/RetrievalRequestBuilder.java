package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds {@link RetrievalRequest} from orchestrated runtime context and {@link QueryPlan} only.
 */
@Component
public class RetrievalRequestBuilder {

    public RetrievalMode resolveMode(RagConfig rag) {
        if (!rag.useRetrieval()) {
            throw new IllegalStateException("RetrievalRequestBuilder requires useRetrieval");
        }
        if (rag.materializationStrategy() == MaterializationStrategy.HYBRID) {
            return RetrievalMode.HYBRID_DENSE_SPARSE;
        }
        return RetrievalMode.DENSE_ONLY;
    }

    public RetrievalRequest build(ExecutionContext ctx, QueryPlan plan) {
        RagConfig rag = ctx.resolved().toRagConfig();
        RetrievalMode mode = resolveMode(rag);
        int topK = rag.topK() > 0 ? rag.topK() : 10;
        int denseFetchLimit = RetrievalPolicy.denseFetchLimit(rag.topK());
        int fusionOutputCap = 2 * topK;
        int postFusionCap = topK;
        int maxChars = rag.advancedRetrievalMaxContextChars() > 0
                ? rag.advancedRetrievalMaxContextChars()
                : RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS;

        List<UUID> snapshotIds = ctx.knowledgeSnapshotSelection().orderedSnapshotIds();
        if (snapshotIds.isEmpty()) {
            throw new IllegalStateException("Retrieval requires non-empty knowledge snapshots");
        }

        List<String> docFilter = ctx.documentFilter();
        boolean allDocs = docFilter.isEmpty()
                || docFilter.stream().anyMatch(s -> s != null && "all".equalsIgnoreCase(s.trim()));

        Optional<String> conv = ctx.conversationId() == null
                ? Optional.empty()
                : Optional.of(ctx.conversationId().toString());

        return new RetrievalRequest(
                plan.rewrittenQueryText(),
                plan.slots(),
                plan.targetEntities(),
                plan.targetAttributes(),
                plan.entityExtractionResult(),
                mode,
                topK,
                topK,
                fusionOutputCap,
                postFusionCap,
                maxChars,
                denseFetchLimit,
                snapshotIds,
                ctx.projectId(),
                conv,
                docFilter,
                allDocs);
    }
}
