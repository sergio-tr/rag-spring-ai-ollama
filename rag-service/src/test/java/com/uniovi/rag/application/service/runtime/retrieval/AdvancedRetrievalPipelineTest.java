package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.application.exception.RagServiceException;
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
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.retrieval.CompressionOutcome;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvancedRetrievalPipelineTest {

    @Mock
    private RetrievalRequestBuilder retrievalRequestBuilder;

    @Mock
    private DenseRetrievalStrategy denseRetrievalStrategy;

    @Mock
    private HybridRetrievalStrategy hybridRetrievalStrategy;

    @Mock
    private RetrievalFusionService retrievalFusionService;

    @Mock
    private RetrievalReranker retrievalReranker;

    @Mock
    private RetrievalFilter retrievalFilter;

    @Mock
    private ContextCompressionStrategy contextCompressionStrategy;

    @Mock
    private RetrievalPromptTextBuilder retrievalPromptTextBuilder;

    @Mock
    private MetadataAppendixLoader metadataAppendixLoader;

    @InjectMocks
    private AdvancedRetrievalPipeline pipeline;

    @Test
    void retrieve_denseOnlyEmpty_addsRetrievalEmptyNote() {
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = executionContext(sid);
        QueryPlan plan = minimalPlan();
        RetrievalRequest req = retrievalRequest(sid, RetrievalMode.DENSE_ONLY);

        when(retrievalRequestBuilder.build(ctx, plan)).thenReturn(req);
        when(denseRetrievalStrategy.retrieve(req)).thenReturn(List.of());
        when(retrievalReranker.rerank(eq(req), eq(plan), eq(List.of())))
                .thenReturn(new RetrievalReranker.RerankResult(List.of(), List.of()));
        when(retrievalFilter.filter(eq(req), eq(plan), eq(List.of()))).thenReturn(List.of());
        when(contextCompressionStrategy.compress(eq(List.of()), anyInt()))
                .thenReturn(
                        new ContextCompressionStrategy.CompressionResult(
                                List.of(), new CompressionOutcome(0, 0, 0, List.of())));
        when(retrievalPromptTextBuilder.build(any(), any(), any())).thenReturn("");

        var out = pipeline.retrieve(ctx, plan, "DocumentDenseRagWorkflow");

        assertThat(out.traceNotes()).contains("retrieval_empty_result");
    }

    @Test
    void retrieve_hybridSparseFailure_propagatesRagServiceException() {
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx = executionContext(sid);
        QueryPlan plan = minimalPlan();
        RetrievalRequest req = retrievalRequest(sid, RetrievalMode.HYBRID_DENSE_SPARSE);

        when(retrievalRequestBuilder.build(ctx, plan)).thenReturn(req);
        when(hybridRetrievalStrategy.dense(req)).thenReturn(List.of());
        when(hybridRetrievalStrategy.sparse(req))
                .thenThrow(RagServiceException.hybridSparseRetrievalFailed(new RuntimeException("db")));

        assertThatThrownBy(() -> pipeline.retrieve(ctx, plan, "ChunkDenseRagWorkflow"))
                .isInstanceOf(RagServiceException.class);
    }

    private static ExecutionContext executionContext(UUID snapshotId) {
        RagConfig rag = baseRag();
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
                "u",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                new KnowledgeSnapshotSelection(
                        List.of(snapshotId), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                "c",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static RagConfig baseRag() {
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
                false,
                5,
                0.7,
                "l",
                "e",
                "c",
                "r",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    private static QueryPlan minimalPlan() {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of());
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "rewritten",
                "L",
                Optional.empty(),
                ClassifierStatus.DISABLED,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                entities,
                StructuredRewriteResult.identityDisabled("r", "r"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }

    private static RetrievalRequest retrievalRequest(UUID snapshotId, RetrievalMode mode) {
        return new RetrievalRequest(
                "q",
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                mode,
                5,
                5,
                10,
                5,
                24_000,
                RetrievalPolicy.denseFetchLimit(5),
                List.of(snapshotId),
                UUID.randomUUID(),
                Optional.empty(),
                List.of("all"),
                true);
    }
}
