package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalFilterTest {

    private final RetrievalFilter filter = new RetrievalFilter();

    @Test
    void filter_dropsWrongSnapshotAndSlotMismatch() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        RetrievalRequest req = request(List.of(s1), RetrievalMode.DENSE_ONLY);
        QueryPlan qp = planWithSlots(Map.of("k", "v"));
        RetrievalCandidate ok =
                new RetrievalCandidate(
                        s1 + ":d:0",
                        "t",
                        Map.of("document_id", "d", "indexSnapshotId", s1.toString(), "k", "v"),
                        1,
                        Double.NaN,
                        1,
                        0,
                        s1,
                        1);
        RetrievalCandidate badSnap =
                new RetrievalCandidate(
                        s2 + ":d:0",
                        "t",
                        Map.of("document_id", "d", "indexSnapshotId", s2.toString(), "k", "v"),
                        1,
                        Double.NaN,
                        1,
                        0,
                        s2,
                        1);
        RetrievalCandidate badSlot =
                new RetrievalCandidate(
                        s1 + ":d:0",
                        "t",
                        Map.of("document_id", "d", "indexSnapshotId", s1.toString(), "k", "wrong"),
                        1,
                        Double.NaN,
                        1,
                        0,
                        s1,
                        1);

        List<RetrievalCandidate> out = filter.filter(req, qp, List.of(ok, badSnap, badSlot));
        assertThat(out).containsExactly(ok);
    }

    private static QueryPlan planWithSlots(Map<String, String> slots) {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "r",
                "r",
                "r",
                "L",
                Optional.empty(),
                com.uniovi.rag.domain.runtime.query.ClassifierStatus.DISABLED,
                com.uniovi.rag.domain.runtime.query.QueryIntent.UNKNOWN,
                slots,
                List.of(),
                List.of(),
                entities,
                com.uniovi.rag.domain.runtime.query.StructuredRewriteResult.identityDisabled("r", "r"),
                com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape.UNKNOWN,
                com.uniovi.rag.domain.runtime.query.AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }

    private static RetrievalRequest request(List<UUID> snapshots, RetrievalMode mode) {
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
                50,
                snapshots,
                UUID.randomUUID(),
                Optional.empty(),
                List.of("all"),
                true);
    }
}
