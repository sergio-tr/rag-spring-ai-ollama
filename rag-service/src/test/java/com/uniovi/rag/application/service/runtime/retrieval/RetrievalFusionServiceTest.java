package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalFusionMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import com.uniovi.rag.domain.runtime.retrieval.RetrievedContextSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalFusionServiceTest {

    private final RetrievalFusionService fusion = new RetrievalFusionService();

    @Test
    void fuse_rrf_ordersAndCaps() {
        UUID s = UUID.randomUUID();
        RetrievalRequest req = baseRequest(RetrievalMode.HYBRID_DENSE_SPARSE);
        RetrievalCandidate d1 =
                new RetrievalCandidate(
                        s + ":doc1:0",
                        "a",
                        Map.of("document_id", "doc1", "indexSnapshotId", s.toString(), "chunk_index", 0),
                        0.1,
                        Double.NaN,
                        1,
                        0,
                        s,
                        0.01);
        RetrievalCandidate d2 =
                new RetrievalCandidate(
                        s + ":doc2:0",
                        "b",
                        Map.of("document_id", "doc2", "indexSnapshotId", s.toString(), "chunk_index", 0),
                        0.2,
                        Double.NaN,
                        2,
                        0,
                        s,
                        0.02);
        RetrievalCandidate sp =
                new RetrievalCandidate(
                        s + ":doc1:0",
                        "a",
                        Map.of("document_id", "doc1", "indexSnapshotId", s.toString(), "chunk_index", 0),
                        Double.NaN,
                        0.5,
                        0,
                        1,
                        s,
                        0.03);

        RetrievedContextSet out = fusion.fuse(req, List.of(d1, d2), List.of(sp));

        assertThat(out.fusionModeUsed()).contains(RetrievalFusionMode.RRF_ONLY);
        assertThat(out.denseInputCount()).isEqualTo(2);
        assertThat(out.sparseInputCount()).isEqualTo(1);
        assertThat(out.bothCount()).isEqualTo(1);
        assertThat(out.denseOnlyCount()).isEqualTo(1);
        assertThat(out.sparseOnlyCount()).isZero();
        assertThat(out.candidates()).hasSize(2);
        assertThat(out.candidates().getFirst().candidateId()).isEqualTo(d1.candidateId());
        assertThat(out.candidateOriginsSummary()).contains("both=1");
        assertThat(out.candidates().getFirst().metadata().get("retrievalOrigin")).isEqualTo("BOTH");
    }

    @Test
    void fuse_deduplicatesSharedChunkId() {
        UUID s = UUID.randomUUID();
        RetrievalRequest req = baseRequest(RetrievalMode.HYBRID_DENSE_SPARSE);
        String sharedId = s + ":doc1:0";
        RetrievalCandidate dense =
                new RetrievalCandidate(
                        sharedId,
                        "dense body",
                        Map.of("document_id", "doc1", "indexSnapshotId", s.toString(), "chunk_index", 0),
                        0.1,
                        Double.NaN,
                        1,
                        0,
                        s,
                        0.01);
        RetrievalCandidate sparse =
                new RetrievalCandidate(
                        sharedId,
                        "sparse body longer",
                        Map.of("document_id", "doc1", "indexSnapshotId", s.toString(), "chunk_index", 0),
                        Double.NaN,
                        0.5,
                        0,
                        1,
                        s,
                        0.03);

        RetrievedContextSet out = fusion.fuse(req, List.of(dense), List.of(sparse));

        assertThat(out.candidates()).hasSize(1);
        assertThat(out.fusedCount()).isEqualTo(1);
        assertThat(out.bothCount()).isEqualTo(1);
    }

    @Test
    void fuse_denseOnlyFallback_whenSparseEmpty() {
        UUID s = UUID.randomUUID();
        RetrievalRequest req = baseRequest(RetrievalMode.HYBRID_DENSE_SPARSE);
        RetrievalCandidate d1 =
                new RetrievalCandidate(
                        s + ":doc1:0",
                        "a",
                        Map.of("document_id", "doc1", "indexSnapshotId", s.toString(), "chunk_index", 0),
                        0.1,
                        Double.NaN,
                        1,
                        0,
                        s,
                        0.01);

        RetrievalFusionService.FusionResult result = fusion.fuseWithTelemetry(req, List.of(d1), List.of());

        assertThat(result.retrieved().sparseInputCount()).isZero();
        assertThat(result.telemetry().fusionStrategy()).isEqualTo("DENSE_ONLY_FALLBACK");
        assertThat(result.telemetry().hybridApplied()).isFalse();
    }

    @Test
    void fuse_hybridApplied_requiresBothDenseAndSparseLegs() {
        UUID s = UUID.randomUUID();
        RetrievalRequest req = baseRequest(RetrievalMode.HYBRID_DENSE_SPARSE);
        RetrievalCandidate denseOnly =
                new RetrievalCandidate(
                        s + ":doc1:0",
                        "a",
                        Map.of("document_id", "doc1", "indexSnapshotId", s.toString(), "chunk_index", 0),
                        0.1,
                        Double.NaN,
                        1,
                        0,
                        s,
                        0.01);
        RetrievalCandidate sparseOnly =
                new RetrievalCandidate(
                        s + ":doc2:0",
                        "b",
                        Map.of("document_id", "doc2", "indexSnapshotId", s.toString(), "chunk_index", 0),
                        Double.NaN,
                        0.5,
                        0,
                        1,
                        s,
                        0.03);

        RetrievalFusionService.FusionResult result =
                fusion.fuseWithTelemetry(req, List.of(denseOnly), List.of(sparseOnly));

        assertThat(result.telemetry().fusionStrategy()).isEqualTo("RRF");
        assertThat(result.telemetry().hybridApplied()).isTrue();
        assertThat(result.retrieved().candidateOriginsSummary()).contains("both=0");
    }

    @Test
    void fuse_sparseOnlyFallback_doesNotMarkHybridApplied() {
        UUID s = UUID.randomUUID();
        RetrievalRequest req = baseRequest(RetrievalMode.HYBRID_DENSE_SPARSE);
        RetrievalCandidate sp =
                new RetrievalCandidate(
                        s + ":doc1:0",
                        "a",
                        Map.of("document_id", "doc1", "indexSnapshotId", s.toString(), "chunk_index", 0),
                        Double.NaN,
                        0.5,
                        0,
                        1,
                        s,
                        0.03);

        RetrievalFusionService.FusionResult result = fusion.fuseWithTelemetry(req, List.of(), List.of(sp));

        assertThat(result.telemetry().fusionStrategy()).isEqualTo("SPARSE_ONLY");
        assertThat(result.telemetry().hybridApplied()).isFalse();
    }

    private static RetrievalRequest baseRequest(RetrievalMode mode) {
        UUID sid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        return new RetrievalRequest(
                "query",
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
                50,
                List.of(sid),
                pid,
                Optional.empty(),
                List.of("all"), true, Optional.empty());
    }
}
