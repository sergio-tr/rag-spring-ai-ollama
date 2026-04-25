package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;

public interface ExecutionWorkflow {

    RagExecutionResult execute(ExecutionContext ctx);

    /** Frozen class name for traces (e.g. {@code DirectLlmWorkflow}). */
    String workflowName();
}
