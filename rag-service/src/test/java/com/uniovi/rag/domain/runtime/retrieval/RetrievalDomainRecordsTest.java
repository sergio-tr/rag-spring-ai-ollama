package com.uniovi.rag.domain.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalDomainRecordsTest {

    @Test
    void retrievedContextSet_normalizesNullOptionalFusion() {
        RetrievalCandidate c = sampleCandidate("c1");
        RetrievedContextSet set =
                new RetrievedContextSet(List.of(c), null, 1, 2, 3);
        assertTrue(set.fusionModeUsed().isEmpty());
        assertEquals(1, set.denseInputCount());
        assertThrows(NullPointerException.class, () -> new RetrievedContextSet(null, Optional.empty(), 0, 0, 0));
    }

    @Test
    void retrievalDiagnostics_normalizesNullOptionalAndSnapshotString() {
        RetrievalDiagnostics d =
                new RetrievalDiagnostics(
                        RetrievalMode.HYBRID_DENSE_SPARSE,
                        null,
                        null,
                        1,
                        2,
                        3,
                        4,
                        4,
                        5,
                        6,
                        0,
                        0,
                        false,
                        List.of(),
                        List.of(),
                        Optional.empty());
        assertTrue(d.fusionMode().isEmpty());
        assertEquals("", d.snapshotIdsJoined());
    }

    @Test
    void compressionOutcome_defensiveCopy() {
        CompressionOutcome o = new CompressionOutcome(10, 5, 1, List.of("r"));
        assertThrows(NullPointerException.class, () -> new CompressionOutcome(0, 0, 0, null));
        assertThrows(UnsupportedOperationException.class, () -> o.rulesApplied().add("x"));
    }

    @Test
    void retrievalCandidate_normalizesNullContent() {
        UUID sid = UUID.randomUUID();
        RetrievalCandidate c =
                new RetrievalCandidate("id", null, Map.of(), 0.1, 0.2, 1, 2, sid, 0.3);
        assertEquals("", c.content());
        assertThrows(NullPointerException.class, () -> new RetrievalCandidate(null, "x", Map.of(), 0, 0, 0, 0, sid, 0));
    }

    @Test
    void retrievalRequest_buildsWithMinimalInputs() {
        UUID projectId = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "query",
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote("n"),
                        RetrievalMode.DENSE_ONLY,
                        1,
                        2,
                        3,
                        4,
                        100,
                        5,
                        List.of(UUID.randomUUID()),
                        projectId,
                        Optional.of("conv-1"),
                        List.of("doc-1"),
                        false);
        assertEquals("query", req.queryText());
        assertTrue(req.conversationId().isPresent());
    }

    @Test
    void curatedContextSet_happyPathWithEmptyTraces() {
        RetrievalCandidate c = sampleCandidate("rc");
        CompressionOutcome compression = new CompressionOutcome(100, 50, 0, List.of());
        RetrievalDiagnostics diag =
                new RetrievalDiagnostics(
                        RetrievalMode.DENSE_ONLY,
                        Optional.of(RetrievalFusionMode.RRF_ONLY),
                        "snap",
                        1,
                        0,
                        1,
                        1,
                        1,
                        1,
                        1,
                        0,
                        0,
                        true,
                        List.of("rc"),
                        List.of("rc"),
                        Optional.of("rc:0.50"));
        CuratedContextSet curated =
                new CuratedContextSet(
                        List.of(c),
                        "prompt",
                        compression,
                        List.of("note"),
                        diag,
                        List.of(new RerankOutcome("id", 0.5, 1)),
                        List.of());
        assertEquals(1, curated.finalCandidates().size());
        assertEquals("prompt", curated.promptContextText());
    }

    private static RetrievalCandidate sampleCandidate(String id) {
        return new RetrievalCandidate(
                id, "body", Map.of(), 0.0, 0.0, 0, 0, UUID.randomUUID(), 0.0);
    }
}
