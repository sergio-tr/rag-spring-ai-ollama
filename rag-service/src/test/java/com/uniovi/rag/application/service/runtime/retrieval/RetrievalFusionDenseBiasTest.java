package com.uniovi.rag.application.service.runtime.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetrievalFusionDenseBiasTest {

    private final RetrievalFusionService fusion = new RetrievalFusionService();

    @Test
    void denseTopCandidateWinsWhenSparseIntroducesUnrelatedHits() {
        UUID s = UUID.randomUUID();
        RetrievalRequest req = baseRequest();
        RetrievalCandidate denseBest =
                new RetrievalCandidate(
                        s + ":acta5:0",
                        "Presidente Jorge Moreno Navarro 25 febrero 2026",
                        Map.of("document_id", "acta5", "chunk_index", 0),
                        0.95,
                        Double.NaN,
                        1,
                        0,
                        s,
                        0.5);
        RetrievalCandidate denseSecond =
                new RetrievalCandidate(
                        s + ":acta1:0",
                        "Acta 1 content",
                        Map.of("document_id", "acta1", "chunk_index", 0),
                        0.7,
                        Double.NaN,
                        2,
                        0,
                        s,
                        0.3);
        RetrievalCandidate sparseNoise1 =
                new RetrievalCandidate(
                        s + ":noise1:0",
                        "unrelated sparse hit one",
                        Map.of("document_id", "noise1", "chunk_index", 0),
                        Double.NaN,
                        0.9,
                        0,
                        1,
                        s,
                        0.01);
        RetrievalCandidate sparseNoise2 =
                new RetrievalCandidate(
                        s + ":noise2:0",
                        "unrelated sparse hit two",
                        Map.of("document_id", "noise2", "chunk_index", 0),
                        Double.NaN,
                        0.85,
                        0,
                        2,
                        s,
                        0.01);

        var result =
                fusion.fuseWithTelemetry(
                        req, List.of(denseBest, denseSecond), List.of(sparseNoise1, sparseNoise2));

        assertThat(result.retrieved().candidates().getFirst().candidateId()).isEqualTo(denseBest.candidateId());
    }

    private static RetrievalRequest baseRequest() {
        UUID sid = UUID.randomUUID();
        return new RetrievalRequest(
                "presidente 25 febrero 2026",
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
                List.of(sid),
                UUID.randomUUID(),
                Optional.empty(),
                List.of("all"),
                true,
                Optional.empty());
    }
}
