package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowNameInferenceRetrievalRouteTest {

    @Test
    void inferRetrievalRoute_p8HybridMetadata_differsFromP4WhileWorkflowNameMayMatch() {
        RagConfig p4 = hybridConfig(MaterializationStrategy.CHUNK_LEVEL, true, false, false);
        RagConfig p8 = hybridConfig(MaterializationStrategy.HYBRID, true, true, true);

        assertThat(WorkflowNameInference.inferWorkflowName(p4)).isEqualTo("ChunkDenseMetadataWorkflow");
        assertThat(WorkflowNameInference.inferWorkflowName(p8)).isEqualTo("ChunkDenseMetadataWorkflow");
        assertThat(WorkflowNameInference.inferRetrievalRoute(p4)).isEqualTo("CHUNK_DENSE_METADATA");
        assertThat(WorkflowNameInference.inferRetrievalRoute(p8)).isEqualTo("HYBRID_DENSE_SPARSE_METADATA");
    }

    @Test
    void inferRetrievalRoute_p3ChunkDense_withoutMetadata() {
        RagConfig p3 = hybridConfig(MaterializationStrategy.CHUNK_LEVEL, false, false, false);

        assertThat(WorkflowNameInference.inferRetrievalRoute(p3)).isEqualTo("CHUNK_DENSE");
        assertThat(WorkflowNameInference.inferWorkflowName(p3)).isEqualTo("ChunkDenseRagWorkflow");
    }

    private static RagConfig hybridConfig(
            MaterializationStrategy strategy,
            boolean metadataEnabled,
            boolean rankerEnabled,
            boolean postRetrievalEnabled) {
        return new RagConfig(
                false,
                false,
                true,
                metadataEnabled,
                false,
                rankerEnabled,
                postRetrievalEnabled,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                10,
                0.7,
                "llm",
                "emb",
                "classifier",
                "reasoning",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                strategy);
    }
}
