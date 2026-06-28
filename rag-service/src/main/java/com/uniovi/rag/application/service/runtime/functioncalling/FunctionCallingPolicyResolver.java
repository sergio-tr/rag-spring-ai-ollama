package com.uniovi.rag.application.service.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingDecision;
import com.uniovi.rag.domain.runtime.query.QueryPlan;

import java.util.Optional;

/** Builds function-calling tool exposure when preset and query preconditions are satisfied. */
public interface FunctionCallingPolicyResolver {

    Optional<FunctionCallingDecision> resolve(ExecutionContext ctx, QueryPlan plan);
}
