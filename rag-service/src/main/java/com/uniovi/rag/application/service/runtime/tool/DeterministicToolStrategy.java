package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;

/**
 * Single entrypoint for deterministic tools in the runtime engine. The orchestrated path invokes this only from
 * {@link com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator}. P18 replay invokes
 * {@link DeterministicToolExecutor} directly with a pinned tool kind (no resolver re-run).
 */
public interface DeterministicToolStrategy {

    DeterministicToolExecutionResult tryExecute(ExecutionContext ctx, QueryPlan plan);
}
