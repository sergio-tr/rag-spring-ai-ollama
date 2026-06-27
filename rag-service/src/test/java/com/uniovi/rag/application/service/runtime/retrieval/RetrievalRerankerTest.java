package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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

    @Test
    void rerank_exactRequestedDateOutranksSimilarWrongYear() {
        UUID s = UUID.randomUUID();
        RetrievalRequest req = fusionRequest("¿Resumen del acta del 25/02/2026?");
        RetrievalCandidate wrongYear =
                new RetrievalCandidate(
                        s + ":a:0",
                        "Fecha: 25 de febrero de 2025. Contenido semanticamente parecido.",
                        Map.of("document_id", "a", "indexSnapshotId", s.toString(), "chunk_index", 0),
                        Double.NaN,
                        Double.NaN,
                        1,
                        0,
                        s,
                        0.20);
        RetrievalCandidate exactDate =
                new RetrievalCandidate(
                        s + ":b:0",
                        "Fecha: 25 de febrero de 2026. Contenido menos parecido.",
                        Map.of("document_id", "b", "indexSnapshotId", s.toString(), "chunk_index", 3),
                        Double.NaN,
                        Double.NaN,
                        2,
                        0,
                        s,
                        0.01);

        var result = reranker.rerank(req, minimalPlan(List.of()), List.of(wrongYear, exactDate));

        assertThat(result.candidates().getFirst().candidateId()).isEqualTo(exactDate.candidateId());
    }

    private static RetrievalRequest fusionRequest() {
        return fusionRequest("q");
    }

    private static RetrievalRequest fusionRequest(String query) {
        UUID sid = UUID.randomUUID();
        return new RetrievalRequest(
                query,
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
                List.of("all"), true, Optional.empty());
    }

    @Test
    void rerank_prefersSynonymTopicMatchOverExactKeywordMiss() {
        UUID s = UUID.randomUUID();
        RetrievalRequest req = fusionRequest("problemas en la instalacion electrica");
        RetrievalCandidate exactKeywordMiss =
                new RetrievalCandidate(
                        s + ":a:0",
                        "se debatio el presupuesto anual",
                        Map.of("document_id", "a", "chunk_index", 0),
                        Double.NaN,
                        Double.NaN,
                        1,
                        0,
                        s,
                        0.05);
        RetrievalCandidate synonymMatch =
                new RetrievalCandidate(
                        s + ":b:0",
                        "se revisaron fallos en la instalacion electrica del edificio",
                        Map.of("document_id", "b", "chunkIndex", 1),
                        Double.NaN,
                        Double.NaN,
                        2,
                        0,
                        s,
                        0.01);
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("electrico"),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of());
        QueryPlan plan = minimalPlan(List.of(), entities);

        var result = reranker.rerank(req, plan, List.of(exactKeywordMiss, synonymMatch));

        assertThat(result.candidates().getFirst().candidateId()).isEqualTo(synonymMatch.candidateId());
    }

    private static QueryPlan minimalPlan(List<String> entities) {
        return minimalPlan(entities, null);
    }

    private static QueryPlan minimalPlan(List<String> entities, EntityExtractionResult entitiesResult) {
        EntityExtractionResult resolved =
                entitiesResult != null
                        ? entitiesResult
                        : new EntityExtractionResult(
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
                "raw",
                "rewritten",
                "L",
                Optional.empty(),
                ClassifierStatus.DISABLED,
                QueryIntent.UNKNOWN,
                Map.of(),
                entities,
                List.of(),
                resolved,
                StructuredRewriteResult.identityDisabled("r", "r"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }
}
