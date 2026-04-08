package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WorkflowSelectorTest {

    private WorkflowSelector selector;

    private static RagConfig rag(boolean useRetrieval, boolean naiveFull, MaterializationStrategy strat, boolean meta) {
        return new RagConfig(
                false,
                false,
                false,
                meta,
                false,
                false,
                false,
                false,
                useRetrieval,
                false,
                5,
                0.2,
                "l",
                "e",
                "c",
                "r",
                naiveFull,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                strat);
    }

    private static ExecutionContext ctx(RagConfig rag) {
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "t",
                List.of("all"),
                Optional.empty(),
                Optional.empty());
    }

    @BeforeEach
    void setUp() {
        ChatClient chatClient = mock(ChatClient.class);
        selector =
                new WorkflowSelector(
                        new DirectLlmWorkflow(chatClient),
                        new FullCorpusWorkflow(chatClient, mock(SnapshotCorpusAssembler.class)),
                        new DocumentDenseRagWorkflow(chatClient, mock(com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline.class)),
                        new ChunkDenseRagWorkflow(chatClient, mock(com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline.class)),
                        new ChunkDenseMetadataWorkflow(chatClient, mock(com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline.class)));
    }

    @Test
    void select_directLlm_whenNoRetrievalAndNoNaiveCorpus() {
        assertTrue(selector.select(ctx(rag(false, false, MaterializationStrategy.CHUNK_LEVEL, false)))
                instanceof DirectLlmWorkflow);
    }

    @Test
    void select_fullCorpus_whenNaiveCorpusWithoutRetrieval() {
        assertTrue(selector.select(ctx(rag(false, true, MaterializationStrategy.CHUNK_LEVEL, false)))
                instanceof FullCorpusWorkflow);
    }

    @Test
    void select_chunkDense_whenChunkLevelWithoutMetadata() {
        assertTrue(selector.select(ctx(rag(true, false, MaterializationStrategy.CHUNK_LEVEL, false)))
                instanceof ChunkDenseRagWorkflow);
    }

    @Test
    void select_chunkDense_whenHybridWithoutMetadata() {
        assertTrue(selector.select(ctx(rag(true, false, MaterializationStrategy.HYBRID, false)))
                instanceof ChunkDenseRagWorkflow);
    }

    @Test
    void select_chunkMetadata_whenHybridWithMetadata() {
        assertTrue(selector.select(ctx(rag(true, false, MaterializationStrategy.HYBRID, true)))
                instanceof ChunkDenseMetadataWorkflow);
    }

    @Test
    void select_allows_whenToolsEnabled() {
        RagFeatureConfiguration f = new RagFeatureConfiguration();
        f.setToolsEnabled(true);
        f.setUseAdvisor(false);
        f.setUseRetrieval(false);
        RagConfig base = RagConfig.fromFeatureConfiguration(f, 5, 0.2, "l", "e", "c", "r");
        assertTrue(selector.select(ctx(base)) instanceof DirectLlmWorkflow);
    }
}
