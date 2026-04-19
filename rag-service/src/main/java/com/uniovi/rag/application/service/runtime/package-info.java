/**
 * Orchestrated RAG runtime: {@link com.uniovi.rag.application.service.runtime.RagExecutionOrchestrator} coordinates
 * route families (workflow, deterministic tools, function calling, advisor) and {@link com.uniovi.rag.application.service.runtime.ExecutionWorkflow}
 * implementations. LLM calls from workflows go through {@link com.uniovi.rag.application.service.runtime.AbstractExecutionWorkflow}
 * (Micrometer {@code rag.ai.llm.invoke} when tracing is enabled).
 */
package com.uniovi.rag.application.service.runtime;
