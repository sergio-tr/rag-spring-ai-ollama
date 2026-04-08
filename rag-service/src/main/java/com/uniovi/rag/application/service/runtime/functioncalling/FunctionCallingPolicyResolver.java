package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.query.QueryPlan;

import java.util.Optional;

/**
 * Builds FC tool exposure when §10.11 items 1–5 already passed.
 */
public interface FunctionCallingPolicyResolver {

    Optional<FunctionCallingDecision> resolve(ExecutionContext ctx, QueryPlan plan, String workflowName);
}
