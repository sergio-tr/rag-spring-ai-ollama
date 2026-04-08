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

class RetrievalRerankerTest {

    private final RetrievalReranker reranker = new RetrievalReranker();

    @Test
    void rerank_ordersByCompositeScore() {
        UUID s = UUID.randomUUID();
        RetrievalRequest req = fusionRequest();
        RetrievalCandidate low =
                new RetrievalCandidate(
                        s + ":a:0",
                        "x",
                        Map.of("document_id", "a", "indexSnapshotId", s.toString(), "chunk_index", 5),
                        Double.NaN,
                        Double.NaN,
                        1,
                        0,
                        s,
                        0.01);
        RetrievalCandidate high =
                new RetrievalCandidate(
                        s + ":b:0",
                        "president speech content",
                        Map.of("document_id", "b", "indexSnapshotId", s.toString(), "chunk_index", 0, "president", "Ada"),
                        Double.NaN,
                        Double.NaN,
                        2,
                        0,
                        s,
                        0.05);
        QueryPlan plan = minimalPlan(List.of("Ada"));

        var result = reranker.rerank(req, plan, List.of(low, high));

        assertThat(result.candidates().getFirst().candidateId()).isEqualTo(high.candidateId());
    }

    private static RetrievalRequest fusionRequest() {
        UUID sid = UUID.randomUUID();
        return new RetrievalRequest(
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
                List.of(sid),
                UUID.randomUUID(),
                Optional.empty(),
                List.of("all"),
                true);
    }

    private static QueryPlan minimalPlan(List<String> entities) {
        EntityExtractionResult entitiesResult =
                new EntityExtractionResult(
                        entities,
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
                com.uniovi.rag.domain.runtime.query.ClassifierStatus.DISABLED,
                com.uniovi.rag.domain.runtime.query.QueryIntent.UNKNOWN,
                Map.of(),
                entities,
                List.of(),
                entitiesResult,
                com.uniovi.rag.domain.runtime.query.StructuredRewriteResult.identityDisabled("r", "r"),
                com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape.UNKNOWN,
                com.uniovi.rag.domain.runtime.query.AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }
}
