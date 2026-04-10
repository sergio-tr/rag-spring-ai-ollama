package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolDecision;

/**
 * Rule-based deterministic tool selection from {@link QueryPlan} only.
 */
public interface DeterministicToolResolver {

    DeterministicToolDecision resolve(ExecutionContext ctx, QueryPlan plan);

    /**
     * Backwards-compatible overload for pre-P13 call sites that passed a workflow name.
     * The deterministic resolver is workflow-independent; the value is ignored.
     */
    default DeterministicToolDecision resolve(ExecutionContext ctx, QueryPlan plan, String workflowName) {
        return resolve(ctx, plan);
    }
}
