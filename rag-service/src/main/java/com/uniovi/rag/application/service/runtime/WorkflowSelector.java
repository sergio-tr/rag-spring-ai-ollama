package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Pure workflow selection from resolved {@link RagConfig} flags and materialization strategy (matrix §10).
 */
@Component
public class WorkflowSelector {

    private final DirectLlmWorkflow directLlmWorkflow;
    private final FullCorpusWorkflow fullCorpusWorkflow;
    private final DocumentDenseRagWorkflow documentDenseRagWorkflow;
    private final ChunkDenseRagWorkflow chunkDenseRagWorkflow;
    private final ChunkDenseMetadataWorkflow chunkDenseMetadataWorkflow;

    public WorkflowSelector(
            DirectLlmWorkflow directLlmWorkflow,
            FullCorpusWorkflow fullCorpusWorkflow,
            DocumentDenseRagWorkflow documentDenseRagWorkflow,
            ChunkDenseRagWorkflow chunkDenseRagWorkflow,
            ChunkDenseMetadataWorkflow chunkDenseMetadataWorkflow) {
        this.directLlmWorkflow = directLlmWorkflow;
        this.fullCorpusWorkflow = fullCorpusWorkflow;
        this.documentDenseRagWorkflow = documentDenseRagWorkflow;
        this.chunkDenseRagWorkflow = chunkDenseRagWorkflow;
        this.chunkDenseMetadataWorkflow = chunkDenseMetadataWorkflow;
    }

    public ExecutionWorkflow select(ExecutionContext ctx) {
        RagConfig rag = ctx.resolved().toRagConfig();
        if (rag.useAdvisor() && !rag.useRetrieval()) {
            throw RagServiceException.unsupportedRuntimeConfiguration("useAdvisor requires useRetrieval and a dense retrieval workflow");
        }
        if (rag.reasoningEnabled() || rag.rankerEnabled() || rag.postRetrievalEnabled()) {
            throw RagServiceException.unsupportedRuntimeConfiguration("advanced runtime capabilities are not implemented");
        }
        MaterializationStrategy strategy = rag.materializationStrategy();
        if (rag.useRetrieval() && strategy == MaterializationStrategy.STRUCTURED_SEARCH) {
            throw RagServiceException.unsupportedRuntimeConfiguration("STRUCTURED_SEARCH materialization with retrieval");
        }

        if (!rag.useRetrieval() && !rag.naiveFullCorpusInPromptEnabled()) {
            return directLlmWorkflow;
        }
        if (!rag.useRetrieval() && rag.naiveFullCorpusInPromptEnabled()) {
            return fullCorpusWorkflow;
        }
        if (rag.useRetrieval() && strategy == MaterializationStrategy.DOCUMENT_LEVEL) {
            return documentDenseRagWorkflow;
        }
        if (rag.useRetrieval() && strategy == MaterializationStrategy.CHUNK_LEVEL && !rag.metadataEnabled()) {
            return chunkDenseRagWorkflow;
        }
        if (rag.useRetrieval() && strategy == MaterializationStrategy.CHUNK_LEVEL && rag.metadataEnabled()) {
            return chunkDenseMetadataWorkflow;
        }
        if (rag.useRetrieval() && strategy == MaterializationStrategy.HYBRID && !rag.metadataEnabled()) {
            return chunkDenseRagWorkflow;
        }
        if (rag.useRetrieval() && strategy == MaterializationStrategy.HYBRID && rag.metadataEnabled()) {
            return chunkDenseMetadataWorkflow;
        }
        throw RagServiceException.unsupportedRuntimeConfiguration("no matching workflow for flags");
    }
}
