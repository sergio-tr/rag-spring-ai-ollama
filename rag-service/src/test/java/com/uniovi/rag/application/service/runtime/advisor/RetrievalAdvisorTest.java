package com.uniovi.rag.application.service.runtime.advisor;

import com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.CuratedContextSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalAdvisorTest {

    @Test
    void retrieve_delegates_to_advanced_pipeline_once() {
        AdvancedRetrievalPipeline pipeline = mock(AdvancedRetrievalPipeline.class);
        RetrievalAdvisor advisor = new RetrievalAdvisor(pipeline);

        ExecutionContext ctx = mock(ExecutionContext.class);
        QueryPlan plan = mock(QueryPlan.class);
        CuratedContextSet curated = mock(CuratedContextSet.class);
        when(pipeline.retrieve(ctx, plan, "ChunkDenseRagWorkflow")).thenReturn(curated);

        assertSame(curated, advisor.retrieve(ctx, plan, "ChunkDenseRagWorkflow"));
        verify(pipeline).retrieve(eq(ctx), eq(plan), eq("ChunkDenseRagWorkflow"));
    }
}
