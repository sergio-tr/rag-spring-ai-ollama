package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridRetrievalStrategyTest {

    @Mock
    private DenseRetrievalStrategy denseRetrievalStrategy;

    @Mock
    private SparseRetrievalStrategy sparseRetrievalStrategy;

    @InjectMocks
    private HybridRetrievalStrategy hybridRetrievalStrategy;

    @Test
    void denseAndSparse_delegateToLegs() {
        UUID s = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "q",
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        RetrievalMode.HYBRID_DENSE_SPARSE,
                        5,
                        5,
                        10,
                        5,
                        24_000,
                        50,
                        List.of(s),
                        UUID.randomUUID(),
                        Optional.empty(),
                        List.of("all"),
                        true);
        RetrievalCandidate d =
                new RetrievalCandidate(
                        s + ":a:0",
                        "d",
                        Map.of("indexSnapshotId", s.toString(), "document_id", "a"),
                        1,
                        Double.NaN,
                        1,
                        0,
                        s,
                        0.1);
        RetrievalCandidate sp =
                new RetrievalCandidate(
                        s + ":b:0",
                        "s",
                        Map.of("indexSnapshotId", s.toString(), "document_id", "b"),
                        Double.NaN,
                        1,
                        0,
                        1,
                        s,
                        0.1);
        when(denseRetrievalStrategy.retrieve(req)).thenReturn(List.of(d));
        when(sparseRetrievalStrategy.retrieve(req)).thenReturn(List.of(sp));

        assertThat(hybridRetrievalStrategy.dense(req)).containsExactly(d);
        assertThat(hybridRetrievalStrategy.sparse(req)).containsExactly(sp);
        verify(denseRetrievalStrategy).retrieve(req);
        verify(sparseRetrievalStrategy).retrieve(req);
    }
}
