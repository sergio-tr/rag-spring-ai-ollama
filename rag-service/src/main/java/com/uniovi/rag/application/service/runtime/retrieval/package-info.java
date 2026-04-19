/**
 * Snapshot-bound retrieval for orchestrated dense workflows.
 * <p>
 * {@link com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline} is the single entrypoint
 * for hybrid/dense retrieval used by {@code DocumentDenseRagWorkflow}, {@code ChunkDenseRagWorkflow}, and
 * {@code ChunkDenseMetadataWorkflow}. It must not duplicate pgvector or FTS access paths owned by
 * {@link com.uniovi.rag.application.service.runtime.retrieval.DenseRetrievalStrategy} /
 * {@link com.uniovi.rag.application.service.runtime.retrieval.HybridRetrievalStrategy}.
 */
package com.uniovi.rag.application.service.runtime.retrieval;
