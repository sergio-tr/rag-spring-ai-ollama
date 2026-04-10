package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DenseRetrievalStrategyTest {

    @Mock
    private PgVectorStore vectorStore;

    private DenseRetrievalStrategy denseRetrievalStrategy;

    @BeforeEach
    void setLegacyContext() {
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
                com.uniovi.rag.domain.knowledge.MaterializationStrategy.CHUNK_LEVEL);
        RagExecutionContextHolder.set(RagExecutionContext.forLegacyPipeline(rag, "t"));
        denseRetrievalStrategy = new DenseRetrievalStrategy(vectorStore, 10, 0.7);
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
                        true);
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
                        true);
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
}
