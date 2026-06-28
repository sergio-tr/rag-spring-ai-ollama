package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.domain.runtime.retrieval.SparseRetrievalTelemetry;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Runs dense and sparse legs for hybrid mode (fusion is performed separately).
 */
@Service
public class HybridRetrievalStrategy {

    private final DenseRetrievalStrategy denseRetrievalStrategy;
    private final SparseRetrievalStrategy sparseRetrievalStrategy;

    public HybridRetrievalStrategy(
            DenseRetrievalStrategy denseRetrievalStrategy, SparseRetrievalStrategy sparseRetrievalStrategy) {
        this.denseRetrievalStrategy = denseRetrievalStrategy;
        this.sparseRetrievalStrategy = sparseRetrievalStrategy;
    }

    public List<RetrievalCandidate> dense(RetrievalRequest req) {
        return denseRetrievalStrategy.retrieve(req);
    }

    public List<RetrievalCandidate> sparse(RetrievalRequest req) {
        return sparseRetrievalStrategy.retrieve(req);
    }

    public SparseRetrievalOutcome sparseWithOutcome(RetrievalRequest req, QueryPlan plan) {
        return sparseRetrievalStrategy.retrieve(req, plan);
    }
}
