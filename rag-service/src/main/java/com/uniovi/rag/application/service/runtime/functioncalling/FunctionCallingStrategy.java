package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;

/**
 * Function-calling runtime entrypoint. Returns a full result so the orchestrator can record trace for every
 * terminal outcome; {@link FunctionCallingExecutionResult#shortCircuited()} is true only for {@code EXECUTED_SUCCESS}.
 */
public interface FunctionCallingStrategy {

    FunctionCallingExecutionResult tryExecute(
            ExecutionContext ctx, QueryPlan plan, FunctionCallingDecision decision);
}
