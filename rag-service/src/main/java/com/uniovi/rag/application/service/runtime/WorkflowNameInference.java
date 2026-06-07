package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;

/**
 * Stable workflow naming for exports and telemetry when Spring beans are unavailable (Lab benchmark metrics_payload).
 * Mirrors {@link WorkflowSelector} routing without executing workflows.
 */
public final class WorkflowNameInference {

    private WorkflowNameInference() {}

    /**
     * Returns the workflow bean name string that {@link WorkflowSelector} would choose for {@code rag}, or a sentinel
     * when the combination is unsupported by the matrix.
     */
    public static String inferWorkflowName(RagConfig rag) {
        if (rag == null) {
            return null;
        }
        if (rag.useAdvisor() && !rag.useRetrieval()) {
            return "UNSUPPORTED_CONFIGURATION";
        }
        if (rag.rankerEnabled() && !rag.useRetrieval()) {
            return "UNSUPPORTED_CONFIGURATION";
        }
        if (rag.postRetrievalEnabled() && !rag.useRetrieval()) {
            return "UNSUPPORTED_CONFIGURATION";
        }
        MaterializationStrategy strategy = rag.materializationStrategy();
        if (rag.useRetrieval() && strategy == MaterializationStrategy.STRUCTURED_SEARCH) {
            return "UNSUPPORTED_CONFIGURATION";
        }

        if (!rag.useRetrieval() && !rag.naiveFullCorpusInPromptEnabled()) {
            return "DirectLlmWorkflow";
        }
        if (!rag.useRetrieval() && rag.naiveFullCorpusInPromptEnabled()) {
            return rag.corpusGroundedDirectWorkflow() ? "CorpusGroundedDirectWorkflow" : "FullCorpusWorkflow";
        }
        if (rag.useRetrieval() && strategy == MaterializationStrategy.DOCUMENT_LEVEL) {
            return "DocumentDenseRagWorkflow";
        }
        if (rag.useRetrieval() && strategy == MaterializationStrategy.CHUNK_LEVEL && !rag.metadataEnabled()) {
            return "ChunkDenseRagWorkflow";
        }
        if (rag.useRetrieval() && strategy == MaterializationStrategy.CHUNK_LEVEL && rag.metadataEnabled()) {
            return "ChunkDenseMetadataWorkflow";
        }
        if (rag.useRetrieval() && strategy == MaterializationStrategy.HYBRID && !rag.metadataEnabled()) {
            return "ChunkDenseRagWorkflow";
        }
        if (rag.useRetrieval() && strategy == MaterializationStrategy.HYBRID && rag.metadataEnabled()) {
            return "ChunkDenseMetadataWorkflow";
        }
        return "UNSUPPORTED_CONFIGURATION";
    }
}
