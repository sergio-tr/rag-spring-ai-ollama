package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.application.service.knowledge.EmbeddingIndexCompatibilityService;
import com.uniovi.rag.application.service.knowledge.document.KnowledgeChunkMetadataFactory;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.infrastructure.vector.PgVectorStoreRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DenseRetrievalStrategyTest {

    @Mock
    private PgVectorStore vectorStore;

    @Mock
    private PgVectorStoreRegistry vectorStoreRegistry;

    @Mock
    private RagVectorProperties ragVectorProperties;

    @Mock
    private EmbeddingIndexCompatibilityService embeddingIndexCompatibilityService;

    private DenseRetrievalStrategy denseRetrievalStrategy;

    private static EmbeddingIndexCompatibilityService permissiveCompatibility() {
        EmbeddingIndexCompatibilityService svc = mock(EmbeddingIndexCompatibilityService.class);
        lenient().doNothing().when(svc).assertRetrievalCompatible(any());
        return svc;
    }

    @BeforeEach
    void setUnscopedProjectContext() {
        RagConfig rag =
                new RagConfig(
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
        RagExecutionContextHolder.set(RagExecutionContext.forUnscopedExecution(rag, "t"));
        lenient().when(ragVectorProperties.requireSnapshotEmbeddingModelId()).thenReturn(false);
        lenient().when(vectorStoreRegistry.forEmbeddingModelId(anyString())).thenReturn(vectorStore);
        denseRetrievalStrategy =
                new DenseRetrievalStrategy(
                        vectorStoreRegistry, vectorStore, ragVectorProperties, permissiveCompatibility(), 10, 0.7);
    }

    @AfterEach
    void clearContext() {
        RagExecutionContextHolder.clear();
    }

    @Test
    void retrieve_usesDenseFetchLimitAsVectorTopK() {
        UUID sid = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "q",
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        RetrievalMode.DENSE_ONLY,
                        5,
                        5,
                        10,
                        5,
                        24_000,
                        RetrievalPolicy.denseFetchLimit(5),
                        List.of(sid),
                        UUID.randomUUID(),
                        Optional.empty(),
                        List.of("all"),
                        true,
                        Optional.empty());
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        denseRetrievalStrategy.retrieve(req);

        ArgumentCaptor<SearchRequest> cap = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(cap.capture());
        assertThat(cap.getValue().getTopK()).isEqualTo(RetrievalPolicy.denseFetchLimit(5));
    }

    @Test
    void retrieve_filtersOutNonMatchingSnapshot() {
        UUID sid = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "q",
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        RetrievalMode.DENSE_ONLY,
                        5,
                        5,
                        10,
                        5,
                        24_000,
                        500,
                        List.of(sid),
                        UUID.randomUUID(),
                        Optional.empty(),
                        List.of("all"),
                        true,
                        Optional.empty());
        Document wrong =
                new Document(
                        "x",
                        Map.of("indexSnapshotId", UUID.randomUUID().toString(), "document_id", "d1", "chunk_index", 0));
        Document ok =
                new Document(
                        "y",
                        Map.of("indexSnapshotId", sid.toString(), "document_id", "d2", "chunk_index", 1));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(wrong, ok));

        var out = denseRetrievalStrategy.retrieve(req);

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().snapshotId()).isEqualTo(sid);
    }

    @Test
    void retrieve_usesSimilarityThresholdFromResolvedConfig() {
        RagExecutionContextHolder.clear();
        RagConfig rag =
                new RagConfig(
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
                        5,
                        0.88,
                        "l",
                        "e",
                        "c",
                        "r",
                        false,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);
        RagExecutionContextHolder.set(RagExecutionContext.forUnscopedExecution(rag, "t"));
        denseRetrievalStrategy =
                new DenseRetrievalStrategy(
                        vectorStoreRegistry, vectorStore, ragVectorProperties, permissiveCompatibility(), 10, 0.7);

        UUID sid = UUID.randomUUID();
        RetrievalRequest req = baseRequest(sid, 5);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        denseRetrievalStrategy.retrieve(req);

        ArgumentCaptor<SearchRequest> cap = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(cap.capture());
        assertThat(cap.getValue().getSimilarityThreshold()).isEqualTo(0.88);
    }

    @Test
    void retrieve_filtersDocumentsByProjectIdWhenProjectScoped() {
        RagExecutionContextHolder.clear();
        UUID projectKey = UUID.randomUUID();
        RagConfig rag = baseRag();
        RagExecutionContextHolder.set(
                new RagExecutionContext(null, null, projectKey.toString(), rag, List.of("all"), "trace"));
        denseRetrievalStrategy =
                new DenseRetrievalStrategy(
                        vectorStoreRegistry, vectorStore, ragVectorProperties, permissiveCompatibility(), 10, 0.7);

        UUID sid = UUID.randomUUID();
        RetrievalRequest req = baseRequest(sid, 10);
        Document wrongProject =
                new Document(
                        "a",
                        Map.of(
                                "indexSnapshotId",
                                sid.toString(),
                                "projectId",
                                UUID.randomUUID().toString(),
                                "document_id",
                                "d1",
                                "chunk_index",
                                0));
        Document ok =
                new Document(
                        "b",
                        Map.of(
                                "indexSnapshotId",
                                sid.toString(),
                                "projectId",
                                projectKey.toString(),
                                "document_id",
                                "d2",
                                "chunk_index",
                                1));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(wrongProject, ok));

        var out = denseRetrievalStrategy.retrieve(req);
        assertThat(out).hasSize(1);
        assertThat(out.getFirst().content()).isEqualTo("b");
    }

    @Test
    void retrieve_excludesDocumentsWithoutProjectMetadataWhenProjectScoped() {
        RagExecutionContextHolder.clear();
        UUID projectKey = UUID.randomUUID();
        RagExecutionContextHolder.set(
                new RagExecutionContext(null, null, projectKey.toString(), baseRag(), List.of("all"), "trace"));
        denseRetrievalStrategy =
                new DenseRetrievalStrategy(
                        vectorStoreRegistry, vectorStore, ragVectorProperties, permissiveCompatibility(), 10, 0.7);

        UUID sid = UUID.randomUUID();
        RetrievalRequest req = baseRequest(sid, 10);
        Document noProjectMeta =
                new Document(
                        "historical_query",
                        Map.of(
                                "indexSnapshotId", sid.toString(),
                                "document_id", "d1",
                                "chunk_index", 0));
        Document ok =
                new Document(
                        "scoped",
                        Map.of(
                                "indexSnapshotId", sid.toString(),
                                "projectId", projectKey.toString(),
                                "document_id", "d2",
                                "chunk_index", 1));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(noProjectMeta, ok));

        var out = denseRetrievalStrategy.retrieve(req);
        assertThat(out).hasSize(1);
        assertThat(out.getFirst().content()).isEqualTo("scoped");
    }

    @Test
    void retrieve_appliesChatLocalCorpusScopeAgainstConversationId() {
        RagExecutionContextHolder.clear();
        UUID projectKey = UUID.randomUUID();
        RagConfig rag = baseRag();
        RagExecutionContextHolder.set(
                new RagExecutionContext("conv-99", null, projectKey.toString(), rag, List.of("all"), "trace"));
        denseRetrievalStrategy =
                new DenseRetrievalStrategy(
                        vectorStoreRegistry, vectorStore, ragVectorProperties, permissiveCompatibility(), 10, 0.7);

        UUID sid = UUID.randomUUID();
        RetrievalRequest req = baseRequest(sid, 10);
        Document wrongConv =
                new Document(
                        "a",
                        Map.of(
                                "indexSnapshotId",
                                sid.toString(),
                                "corpusScope",
                                "CHAT_LOCAL",
                                "conversationId",
                                "other",
                                "document_id",
                                "d1",
                                "chunk_index",
                                0));
        Document ok =
                new Document(
                        "b",
                        Map.of(
                                "indexSnapshotId",
                                sid.toString(),
                                "corpusScope",
                                "CHAT_LOCAL",
                                "conversationId",
                                "conv-99",
                                "document_id",
                                "d2",
                                "chunk_index",
                                1));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(wrongConv, ok));

        var out = denseRetrievalStrategy.retrieve(req);
        assertThat(out).hasSize(1);
        assertThat(out.getFirst().content()).isEqualTo("b");
    }

    @Test
    void retrieve_filtersByDocumentAllowlistWhenNotAllDocuments() {
        RagExecutionContextHolder.clear();
        UUID projectKey = UUID.randomUUID();
        RagConfig rag = baseRag();
        RagExecutionContextHolder.set(
                new RagExecutionContext(
                        null, null, projectKey.toString(), rag, List.of("keep-me", "noise"), "trace"));
        denseRetrievalStrategy =
                new DenseRetrievalStrategy(
                        vectorStoreRegistry, vectorStore, ragVectorProperties, permissiveCompatibility(), 10, 0.7);

        UUID sid = UUID.randomUUID();
        RetrievalRequest req = baseRequest(sid, 10);
        Document dropped =
                new Document(
                        "a",
                        Map.of(
                                "indexSnapshotId",
                                sid.toString(),
                                "projectId",
                                projectKey.toString(),
                                "document_id",
                                "drop-me",
                                "chunk_index",
                                0));
        Document kept =
                new Document(
                        "b",
                        Map.of(
                                "indexSnapshotId",
                                sid.toString(),
                                "projectId",
                                projectKey.toString(),
                                "document_id",
                                "keep-me",
                                "chunk_index",
                                1));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(dropped, kept));

        var out = denseRetrievalStrategy.retrieve(req);
        assertThat(out).extracting(RetrievalCandidate::content).containsExactly("b");
    }

    @Test
    void retrieve_mapsDistanceMetadataToDenseScore() {
        UUID sid = UUID.randomUUID();
        RetrievalRequest req = baseRequest(sid, 5);
        Document doc =
                new Document(
                        "t",
                        Map.of(
                                "indexSnapshotId",
                                sid.toString(),
                                "document_id",
                                "d1",
                                "chunk_index",
                                0,
                                "distance",
                                0.42));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        var out = denseRetrievalStrategy.retrieve(req);
        assertThat(out).hasSize(1);
        assertThat(out.getFirst().denseScore()).isEqualTo(0.42);
    }

    @Test
    void retrieve_stopsAtTopKDense() {
        UUID sid = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "q",
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        RetrievalMode.DENSE_ONLY,
                        2,
                        5,
                        10,
                        5,
                        24_000,
                        RetrievalPolicy.denseFetchLimit(10),
                        List.of(sid),
                        UUID.randomUUID(),
                        Optional.empty(),
                        List.of("all"),
                        true,
                        Optional.empty());
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            docs.add(
                    new Document(
                            "c" + i,
                            Map.of(
                                    "indexSnapshotId",
                                    sid.toString(),
                                    "document_id",
                                    "d" + i,
                                    "chunk_index",
                                    i)));
        }
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(docs);

        var out = denseRetrievalStrategy.retrieve(req);
        assertThat(out).hasSize(2);
    }

    @Test
    void retrieve_appliesSnapshotBoundFilterExpression() {
        UUID sid = UUID.randomUUID();
        RetrievalRequest req = baseRequest(sid, 5);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        denseRetrievalStrategy.retrieve(req);

        ArgumentCaptor<SearchRequest> cap = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(cap.capture());
        assertThat(cap.getValue().hasFilterExpression()).isTrue();
    }

    @Test
    void retrieve_evaluationCorpusMetadataShape_survivesProjectFilter() {
        RagExecutionContextHolder.clear();
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        RagExecutionContextHolder.set(
                new RagExecutionContext(null, null, projectId.toString(), baseRag(), List.of("all"), "trace"));

        Map<String, Object> meta =
                KnowledgeChunkMetadataFactory.buildV2(
                        CorpusScope.PROJECT_SHARED,
                        documentId,
                        projectId,
                        null,
                        sid,
                        "sig",
                        "acta.pdf",
                        2,
                        5,
                        "hash123");
        Document doc = new Document("chunk text", meta);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        DenseRetrievalOutcome outcome = denseRetrievalStrategy.retrieveWithOutcome(baseRequest(sid, 10));

        assertThat(outcome.candidates()).hasSize(1);
        assertThat(outcome.postProjectCandidateCount()).isEqualTo(1);
        assertThat(outcome.candidates().getFirst().candidateId()).endsWith(":2");
    }

    @Test
    void retrieveWithOutcome_reportsStageCounts() {
        UUID sid = UUID.randomUUID();
        Document wrongSnapshot =
                new Document(
                        "noise",
                        Map.of("indexSnapshotId", UUID.randomUUID().toString(), "document_id", "d0", "chunk_index", 0));
        Document ok =
                new Document(
                        "hit",
                        Map.of("indexSnapshotId", sid.toString(), "document_id", "d1", "chunk_index", 1));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(wrongSnapshot, ok));

        DenseRetrievalOutcome outcome = denseRetrievalStrategy.retrieveWithOutcome(baseRequest(sid, 5));

        assertThat(outcome.rawCandidateCount()).isEqualTo(2);
        assertThat(outcome.postSnapshotCandidateCount()).isEqualTo(1);
        assertThat(outcome.candidates()).hasSize(1);
    }

    @Test
    void retrieve_throwsWhenSnapshotEmbeddingModelIdRequiredButMissing() {
        UUID sid = UUID.randomUUID();
        when(ragVectorProperties.requireSnapshotEmbeddingModelId()).thenReturn(true);
        RetrievalRequest req = baseRequest(sid, 5);

        assertThatThrownBy(() -> denseRetrievalStrategy.retrieve(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex -> {
                            ResponseStatusException r = (ResponseStatusException) ex;
                            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                            assertThat(r.getReason()).contains("DENSE_RETRIEVAL_REQUIRES_SNAPSHOT_EMBEDDING_MODEL_ID");
                        });
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

    private static RetrievalRequest baseRequest(UUID snapshotId, int topKDense) {
        return new RetrievalRequest(
                "q",
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                RetrievalMode.DENSE_ONLY,
                topKDense,
                5,
                10,
                5,
                24_000,
                RetrievalPolicy.denseFetchLimit(10),
                List.of(snapshotId),
                UUID.randomUUID(),
                Optional.empty(),
                List.of("all"),
                true,
                Optional.empty());
    }
}
