package com.uniovi.rag.application.service.runtime.advisor;

import com.uniovi.rag.domain.runtime.advisor.AdvisorDecision;
import com.uniovi.rag.domain.runtime.advisor.AdvisorExecutionResult;
import com.uniovi.rag.domain.runtime.advisor.AdvisorKind;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.CuratedContextSet;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Single advisor execution entrypoint for the orchestrated runtime.
 */
@Service
public class AdvisorStrategy {

    private final RetrievalAdvisor retrievalAdvisor;
    private final ContextPackingAdvisor contextPackingAdvisor;

    public AdvisorStrategy(RetrievalAdvisor retrievalAdvisor, ContextPackingAdvisor contextPackingAdvisor) {
        this.retrievalAdvisor = retrievalAdvisor;
        this.contextPackingAdvisor = contextPackingAdvisor;
    }

    public AdvisorExecutionResult execute(
            ExecutionContext ctx, QueryPlan plan, String workflowName, AdvisorDecision decision) {
        if (!decision.selected()) {
            throw new IllegalArgumentException("AdvisorDecision.selected must be true for execute");
        }
        for (AdvisorKind k : decision.executableKinds()) {
            if (k == AdvisorKind.MEMORY_ADVISOR || k == AdvisorKind.ROUTING_ADVISOR) {
                return AdvisorExecutionResult.failedReservedKind(List.of("reserved_kind_in_executable_list"));
            }
        }
        try {
            CuratedContextSet curated = retrievalAdvisor.retrieve(ctx, plan, workflowName);
            try {
                PackedContextSet packed = contextPackingAdvisor.pack(ctx, plan, curated, workflowName);
                return AdvisorExecutionResult.success(packed);
            } catch (RuntimeException e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                return AdvisorExecutionResult.failedPacking(List.of("packing_failed:" + msg));
            }
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return AdvisorExecutionResult.failedRetrieval(List.of("retrieval_failed:" + msg));
        }
    }
}
