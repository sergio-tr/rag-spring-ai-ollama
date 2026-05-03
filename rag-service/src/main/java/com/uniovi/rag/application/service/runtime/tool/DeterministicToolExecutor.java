package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolDecision;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;

/**
 * Executes a selected deterministic tool via the existing tool registry (no QueryAnalyser / classifier).
 */
public interface DeterministicToolExecutor {

    DeterministicToolExecutionResult execute(DeterministicToolDecision decision, ExecutionContext ctx, QueryPlan plan);
}
