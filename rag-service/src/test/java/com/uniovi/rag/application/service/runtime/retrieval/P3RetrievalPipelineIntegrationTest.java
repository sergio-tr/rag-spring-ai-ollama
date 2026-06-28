package com.uniovi.rag.application.service.runtime.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.result.chat.ChatSource;
import com.uniovi.rag.application.result.chat.QueryResponse;
import com.uniovi.rag.application.service.knowledge.document.KnowledgeChunkMetadataFactory;
import com.uniovi.rag.application.service.runtime.ChatSourceMapper;
import com.uniovi.rag.application.service.runtime.RagExecutionMapper;
import com.uniovi.rag.application.service.knowledge.EmbeddingIndexCompatibilityService;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.infrastructure.vector.PgVectorStoreRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import java.util.Optional;

/**
 * End-to-end P3 retrieval propagation: dense search → pipeline → sources → telemetry.
 */
@ExtendWith(MockitoExtension.class)
class P3RetrievalPipelineIntegrationTest {

    @Mock
    private PgVectorStore vectorStore;

    @Mock
    private PgVectorStoreRegistry vectorStoreRegistry;

    @Mock
    private RagVectorProperties ragVectorProperties;

    private DenseRetrievalStrategy denseRetrievalStrategy;

    @BeforeEach
    void setUp() {
        UUID projectId = UUID.randomUUID();
        RagExecutionContextHolder.set(
                new RagExecutionContext(null, null, projectId.toString(), baseRag(), List.of("all"), "trace"));
        when(ragVectorProperties.requireSnapshotEmbeddingModelId()).thenReturn(false);
        EmbeddingIndexCompatibilityService compatibility = mock(EmbeddingIndexCompatibilityService.class);
        lenient().doNothing().when(compatibility).assertRetrievalCompatible(any());
        denseRetrievalStrategy =
                new DenseRetrievalStrategy(vectorStoreRegistry, vectorStore, ragVectorProperties, compatibility, 10, 0.7);
    }

    @AfterEach
    void tearDown() {
        RagExecutionContextHolder.clear();
    }

    @Test
    void p3_denseRetrieval_mapsToQueryResponseWithSourcesAndTelemetry() {
        UUID projectId = UUID.fromString(RagExecutionContextHolder.get().projectId());
        UUID documentId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();

        Map<String, Object> meta =
                KnowledgeChunkMetadataFactory.buildV2(
                        CorpusScope.PROJECT_SHARED,
                        documentId,
                        projectId,
                        null,
                        snapshotId,
                        "sig",
                        "acta.pdf",
                        0,
                        3,
                        "hash");
        Document doc = new Document("Acta content about gas leak discussion.", meta);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        RetrievalRequest req =
                new RetrievalRequest(
                        "gas leak",
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        RetrievalMode.DENSE_ONLY,
                        10,
                        10,
                        20,
                        10,
                        24_000,
                        RetrievalPolicy.denseFetchLimit(10),
                        List.of(snapshotId),
                        projectId,
                        Optional.empty(),
                        List.of("all"),
                        true,
                        Optional.empty());

        DenseRetrievalOutcome outcome = denseRetrievalStrategy.retrieveWithOutcome(req);
        assertThat(outcome.candidates()).isNotEmpty();
        assertThat(outcome.postProjectCandidateCount()).isGreaterThan(0);

        RetrievalCandidate candidate = outcome.candidates().getFirst();
        ChatSource source =
                new ChatSource(
                        String.valueOf(meta.get("documentId")),
                        String.valueOf(meta.get("projectDocumentId")),
                        "acta.pdf",
                        candidate.content(),
                        0.1,
                        "distance",
                        0,
                        null,
                        Map.of("chunkId", candidate.candidateId()));
        RagExecutionResult result =
                RagExecutionResult.withPlaceholderTrace(
                                "answer",
                                "ChunkDenseRagWorkflow",
                                true,
                                false,
                                List.of(snapshotId),
                                null,
                                Optional.empty(),
                                List.of())
                        .withResponseSources(ChatSourceMapper.toPersistedMapsFromInternal(List.of(source)));

        QueryResponse response = RagExecutionMapper.toQueryResponse(result);

        assertThat(response.getSources()).isNotEmpty();
        assertThat(response.getChatTelemetry().get("retrieved_chunk_ids")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> chunkIds = (List<String>) response.getChatTelemetry().get("retrieved_chunk_ids");
        assertThat(chunkIds).isNotEmpty();
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
                false,
                false,
                10,
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
}
