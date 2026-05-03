package com.uniovi.rag.application.service.runtime.advisor;

import com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.CuratedContextSet;
import org.springframework.stereotype.Service;

/**
 * Sole caller of {@link AdvancedRetrievalPipeline#retrieve} on the P10 orchestrated advisor path.
 */
@Service
public class RetrievalAdvisor {

    private final AdvancedRetrievalPipeline advancedRetrievalPipeline;

    public RetrievalAdvisor(AdvancedRetrievalPipeline advancedRetrievalPipeline) {
        this.advancedRetrievalPipeline = advancedRetrievalPipeline;
    }

    public CuratedContextSet retrieve(ExecutionContext ctx, QueryPlan plan, String workflowName) {
        return advancedRetrievalPipeline.retrieve(ctx, plan, workflowName);
    }
}
