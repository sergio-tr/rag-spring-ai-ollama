package com.uniovi.rag.application.service.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline;
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
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvoker;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvokerTestSupport;
import org.springframework.ai.chat.client.ChatClient;

import com.uniovi.rag.configuration.RagRuntimeProperties;

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
                false,
                false,
                false,
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
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "q",
                "q",
                Optional.empty(),
                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                List.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }

    @BeforeEach
    void setUp() {
        RagLlmChatInvoker llmChatInvoker = RagLlmChatInvokerTestSupport.stubContent("ANS");
        selector =
                new WorkflowSelector(
                        new DirectLlmWorkflow(llmChatInvoker, null),
                        new CorpusGroundedDirectWorkflow(
                                llmChatInvoker,
                                mock(SnapshotCorpusAssembler.class),
                                new RuntimePromptBudgeter(new RagRuntimeProperties()),
                                null),
                        new FullCorpusWorkflow(
                                llmChatInvoker,
                                mock(SnapshotCorpusAssembler.class),
                                new RuntimePromptBudgeter(new RagRuntimeProperties()),
                                null),
                        new DocumentDenseRagWorkflow(
                                llmChatInvoker,
                                mock(AdvancedRetrievalPipeline.class),
                                null),
                        new ChunkDenseRagWorkflow(
                                llmChatInvoker,
                                mock(AdvancedRetrievalPipeline.class),
                                null),
                        new ChunkDenseMetadataWorkflow(
                                llmChatInvoker,
                                mock(AdvancedRetrievalPipeline.class),
                                null));
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
    void select_corpusGroundedDirect_whenNaiveCorpusAndDirectWorkflowFlag() throws Exception {
        RagConfig base = rag(false, true, MaterializationStrategy.CHUNK_LEVEL, false);
        RagConfig rag =
                RagConfig.applyJsonOverrides(base, new ObjectMapper().readTree("{\"corpusGroundedDirectWorkflow\": true}"));
        assertTrue(selector.select(ctx(rag)) instanceof CorpusGroundedDirectWorkflow);
    }

    @Test
    void select_fullCorpus_whenNaiveCorpusAndDirectWorkflowFlagExplicitFalse() throws Exception {
        RagConfig base = rag(false, true, MaterializationStrategy.CHUNK_LEVEL, false);
        RagConfig rag =
                RagConfig.applyJsonOverrides(base, new ObjectMapper().readTree("{\"corpusGroundedDirectWorkflow\": false}"));
        assertTrue(selector.select(ctx(rag)) instanceof FullCorpusWorkflow);
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

    @Test
    void select_allows_whenFunctionCallingEnabled() {
        RagFeatureConfiguration f = new RagFeatureConfiguration();
        f.setFunctionCallingEnabled(true);
        f.setUseAdvisor(false);
        f.setUseRetrieval(false);
        RagConfig base = RagConfig.fromFeatureConfiguration(f, 5, 0.2, "l", "e", "c", "r");
        assertTrue(selector.select(ctx(base)) instanceof DirectLlmWorkflow);
    }

    @Test
    void select_allows_useAdvisor_with_dense_retrieval() {
        assertTrue(
                selector.select(ctx(denseChunkWithUseAdvisor(true)))
                        instanceof ChunkDenseRagWorkflow);
    }

    @Test
    void select_rejects_useAdvisor_without_retrieval() {
        RagConfig bad =
                new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                5,
                0.2,
                "l",
                "e",
                "c",
                "r",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.CHUNK_LEVEL);
        assertThrows(RagServiceException.class, () -> selector.select(ctx(bad)));
    }

    @Test
    void select_allows_reasoningEnabled() {
        RagConfig base = rag(true, false, MaterializationStrategy.CHUNK_LEVEL, false);
        RagConfig bad =
                new RagConfig(
                        base.expansionEnabled(),
                        base.nerEnabled(),
                        base.toolsEnabled(),
                        base.metadataEnabled(),
                        true,
                        base.rankerEnabled(),
                        base.postRetrievalEnabled(),
                        base.functionCallingEnabled(),
                        base.useRetrieval(),
                        base.useAdvisor(),
                        base.clarificationEnabled(),
                        base.memoryEnabled(),
                        base.adaptiveRoutingEnabled(),
                        base.judgeEnabled(),
                        base.deterministicToolRoutingEnabled(),
                        base.topK(),
                        base.similarityThreshold(),
                        base.llmModel(),
                        base.embeddingModel(),
                        base.classifierModelId(),
                        base.reasoningStrategy(),
                        base.naiveFullCorpusInPromptEnabled(),
                        base.naiveFullCorpusMaxChars(),
                        base.advancedRetrievalMaxContextChars(),
                        base.corpusGroundedDirectWorkflow(),
                        base.materializationStrategy());
        assertTrue(selector.select(ctx(bad)) instanceof ChunkDenseRagWorkflow);
    }

    @Test
    void select_allows_postRetrievalEnabled_with_retrieval() {
        RagConfig base = rag(true, false, MaterializationStrategy.CHUNK_LEVEL, false);
        RagConfig ok =
                new RagConfig(
                        base.expansionEnabled(),
                        base.nerEnabled(),
                        base.toolsEnabled(),
                        base.metadataEnabled(),
                        base.reasoningEnabled(),
                        base.rankerEnabled(),
                        true,
                        base.functionCallingEnabled(),
                        base.useRetrieval(),
                        base.useAdvisor(),
                        base.clarificationEnabled(),
                        base.memoryEnabled(),
                        base.adaptiveRoutingEnabled(),
                        base.judgeEnabled(),
                        base.deterministicToolRoutingEnabled(),
                        base.topK(),
                        base.similarityThreshold(),
                        base.llmModel(),
                        base.embeddingModel(),
                        base.classifierModelId(),
                        base.reasoningStrategy(),
                        base.naiveFullCorpusInPromptEnabled(),
                        base.naiveFullCorpusMaxChars(),
                        base.advancedRetrievalMaxContextChars(),
                        base.corpusGroundedDirectWorkflow(),
                        base.materializationStrategy());
        assertTrue(selector.select(ctx(ok)) instanceof ChunkDenseRagWorkflow);
    }

    @Test
    void select_allows_rankerEnabled_with_retrieval() {
        RagConfig base = rag(true, false, MaterializationStrategy.CHUNK_LEVEL, false);
        RagConfig ok =
                new RagConfig(
                        base.expansionEnabled(),
                        base.nerEnabled(),
                        base.toolsEnabled(),
                        base.metadataEnabled(),
                        base.reasoningEnabled(),
                        true,
                        base.postRetrievalEnabled(),
                        base.functionCallingEnabled(),
                        base.useRetrieval(),
                        base.useAdvisor(),
                        base.clarificationEnabled(),
                        base.memoryEnabled(),
                        base.adaptiveRoutingEnabled(),
                        base.judgeEnabled(),
                        base.deterministicToolRoutingEnabled(),
                        base.topK(),
                        base.similarityThreshold(),
                        base.llmModel(),
                        base.embeddingModel(),
                        base.classifierModelId(),
                        base.reasoningStrategy(),
                        base.naiveFullCorpusInPromptEnabled(),
                        base.naiveFullCorpusMaxChars(),
                        base.advancedRetrievalMaxContextChars(),
                        base.corpusGroundedDirectWorkflow(),
                        base.materializationStrategy());
        assertTrue(selector.select(ctx(ok)) instanceof ChunkDenseRagWorkflow);
    }

    @Test
    void select_rejects_rankerEnabled_without_retrieval() {
        RagConfig base = rag(false, false, MaterializationStrategy.CHUNK_LEVEL, false);
        RagConfig bad =
                new RagConfig(
                        base.expansionEnabled(),
                        base.nerEnabled(),
                        base.toolsEnabled(),
                        base.metadataEnabled(),
                        base.reasoningEnabled(),
                        true,
                        base.postRetrievalEnabled(),
                        base.functionCallingEnabled(),
                        false,
                        base.useAdvisor(),
                        base.clarificationEnabled(),
                        base.memoryEnabled(),
                        base.adaptiveRoutingEnabled(),
                        base.judgeEnabled(),
                        base.deterministicToolRoutingEnabled(),
                        base.topK(),
                        base.similarityThreshold(),
                        base.llmModel(),
                        base.embeddingModel(),
                        base.classifierModelId(),
                        base.reasoningStrategy(),
                        base.naiveFullCorpusInPromptEnabled(),
                        base.naiveFullCorpusMaxChars(),
                        base.advancedRetrievalMaxContextChars(),
                        base.corpusGroundedDirectWorkflow(),
                        base.materializationStrategy());
        assertThrows(RagServiceException.class, () -> selector.select(ctx(bad)));
    }

    @Test
    void select_rejects_postRetrievalEnabled_without_retrieval() {
        RagConfig base = rag(false, false, MaterializationStrategy.CHUNK_LEVEL, false);
        RagConfig bad =
                new RagConfig(
                        base.expansionEnabled(),
                        base.nerEnabled(),
                        base.toolsEnabled(),
                        base.metadataEnabled(),
                        base.reasoningEnabled(),
                        base.rankerEnabled(),
                        true,
                        base.functionCallingEnabled(),
                        false,
                        base.useAdvisor(),
                        base.clarificationEnabled(),
                        base.memoryEnabled(),
                        base.adaptiveRoutingEnabled(),
                        base.judgeEnabled(),
                        base.deterministicToolRoutingEnabled(),
                        base.topK(),
                        base.similarityThreshold(),
                        base.llmModel(),
                        base.embeddingModel(),
                        base.classifierModelId(),
                        base.reasoningStrategy(),
                        base.naiveFullCorpusInPromptEnabled(),
                        base.naiveFullCorpusMaxChars(),
                        base.advancedRetrievalMaxContextChars(),
                        base.corpusGroundedDirectWorkflow(),
                        base.materializationStrategy());
        assertThrows(RagServiceException.class, () -> selector.select(ctx(bad)));
    }

    private static RagConfig denseChunkWithUseAdvisor(boolean useAdvisor) {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                useAdvisor,
                false,
                false,
                false,
                false,
                5,
                0.2,
                "l",
                "e",
                "c",
                "r",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.CHUNK_LEVEL);
    }
}
