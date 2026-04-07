package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;

/**
 * Single entrypoint for deterministic tools in the runtime engine. Invoked only by {@link
 * com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator}.
 */
public interface DeterministicToolStrategy {

    DeterministicToolExecutionResult tryExecute(ExecutionContext ctx, QueryPlan plan, String workflowName);
}
