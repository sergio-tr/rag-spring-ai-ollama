package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.QueryPlan;

public interface QueryUnderstandingPipeline {

    QueryPlan buildPlan(ExecutionContext ctx);
}

