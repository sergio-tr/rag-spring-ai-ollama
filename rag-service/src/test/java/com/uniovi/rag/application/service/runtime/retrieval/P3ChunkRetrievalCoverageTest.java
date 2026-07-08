package com.uniovi.rag.application.service.runtime.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.knowledge.EmbeddingIndexCompatibilityService;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.infrastructure.vector.PgVectorStoreRegistry;
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

/** P3 dense path: bound bge-m3 snapshot must not use overly strict preset pgvector threshold. */
@ExtendWith(MockitoExtension.class)
class P3ChunkRetrievalCoverageTest {

    @Mock private PgVectorStore vectorStore;
    @Mock private PgVectorStoreRegistry vectorStoreRegistry;
    @Mock private RagVectorProperties ragVectorProperties;

    private DenseRetrievalStrategy denseRetrievalStrategy;

    @BeforeEach
    void setUp() {
        UUID projectId = UUID.randomUUID();
        RagExecutionContextHolder.set(
                new RagExecutionContext(null, null, projectId.toString(), p3Rag(), List.of("all"), "trace"));
        lenient().when(vectorStoreRegistry.forEmbeddingModelId("bge-m3")).thenReturn(vectorStore);
        EmbeddingIndexCompatibilityService compatibility = mock(EmbeddingIndexCompatibilityService.class);
        doNothing().when(compatibility).assertRetrievalCompatible(any());
        denseRetrievalStrategy =
                new DenseRetrievalStrategy(vectorStoreRegistry, vectorStore, ragVectorProperties, compatibility, 10, 0.25);
    }

    @AfterEach
    void tearDown() {
        RagExecutionContextHolder.clear();
    }

    @Test
    void boundSnapshotEmbeddingUsesDeploymentThresholdForVectorSearch() {
        UUID snapshotId = UUID.randomUUID();
        Document doc =
                new Document(
                        "acta ascensor",
                        Map.of(
                                "indexSnapshotId",
                                snapshotId.toString(),
                                "projectId",
                                RagExecutionContextHolder.get().projectId(),
                                "document_id",
                                UUID.randomUUID().toString(),
                                "distance",
                                0.35));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        RetrievalRequest req =
                new RetrievalRequest(
                        "ascensor",
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
                        UUID.fromString(RagExecutionContextHolder.get().projectId()),
                        Optional.empty(),
                        List.of("all"),
                        true,
                        Optional.of("bge-m3"));

        DenseRetrievalOutcome outcome = denseRetrievalStrategy.retrieveWithOutcome(req);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getSimilarityThreshold()).isEqualTo(0.25);
        assertThat(outcome.candidates()).isNotEmpty();
        assertThat(outcome.postProjectCandidateCount()).isGreaterThan(0);
    }

    private static RagConfig p3Rag() {
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
