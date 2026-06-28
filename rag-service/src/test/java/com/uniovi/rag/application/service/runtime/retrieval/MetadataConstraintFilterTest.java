package com.uniovi.rag.application.service.runtime.retrieval;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.MetadataFilterTelemetry;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;

class MetadataConstraintFilterTest {

    private final MetadataConstraintFilter filter = new MetadataConstraintFilter();

    @Test
    void apply_whenHardFilterWouldEmpty_fallsBackToOriginalCandidates() {
        UUID sid = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "Confirma si Jorge Moreno Navarro aparece en el acta del 25 de agosto de 2026.",
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
        QueryPlan plan = planWithPerson("Jorge Moreno Navarro");
        RetrievalCandidate unrelated =
                new RetrievalCandidate("c1", "Sin asistentes relevantes.", Map.of(), 0.1, 0.0, 1, 0, sid, 1.0);

        MetadataConstraintFilter.FilterResult result = filter.apply(req, plan, List.of(unrelated));

        assertThat(result.candidates()).containsExactly(unrelated);
        assertThat(result.telemetry()).isEqualTo(new MetadataFilterTelemetry(true, true));
    }

    @Test
    void apply_whenNoHighConfidenceConstraint_leavesCandidatesUntouched() {
        UUID sid = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "¿Cuántas actas mencionan el ascensor?",
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
        RetrievalCandidate candidate =
                new RetrievalCandidate("c1", "ascensor", Map.of(), 0.1, 0.0, 1, 0, sid, 1.0);

        MetadataConstraintFilter.FilterResult result = filter.apply(req, minimalPlan(), List.of(candidate));

        assertThat(result.candidates()).containsExactly(candidate);
        assertThat(result.telemetry()).isEqualTo(new MetadataFilterTelemetry(false, false));
    }

    @Test
    void apply_metadataDateIsoKeepsMatchingCandidate() {
        UUID sid = UUID.randomUUID();
        RetrievalRequest req =
                new RetrievalRequest(
                        "Confirma la duración del acta del 25/02/2026.",
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
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(),
                        List.of("2026-02-25"),
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of());
        QueryPlan plan = planWithEntities(entities);
        RetrievalCandidate matching =
                new RetrievalCandidate(
                        "c1",
                        "acta sin fecha en texto",
                        Map.of("date_iso", "2026-02-25"),
                        0.1,
                        0.0,
                        1,
                        0,
                        sid,
                        1.0);
        RetrievalCandidate other =
                new RetrievalCandidate(
                        "c2",
                        "otra reunion",
                        Map.of("date_iso", "2025-01-01"),
                        0.2,
                        0.0,
                        2,
                        0,
                        sid,
                        0.5);

        MetadataConstraintFilter.FilterResult result = filter.apply(req, plan, List.of(matching, other));

        assertThat(result.candidates()).containsExactly(matching);
        assertThat(result.telemetry()).isEqualTo(new MetadataFilterTelemetry(true, false));
    }

    private static QueryPlan planWithEntities(EntityExtractionResult entities) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "q",
                "q",
                "q",
                "q",
                "L",
                Optional.empty(),
                ClassifierStatus.DISABLED,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                entities,
                StructuredRewriteResult.identityDisabled("r", "r"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }

    private static QueryPlan planWithPerson(String person) {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(person),
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
                "q",
                "q",
                "q",
                "q",
                "L",
                Optional.empty(),
                ClassifierStatus.DISABLED,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                entities,
                StructuredRewriteResult.identityDisabled("r", "r"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }

    private static QueryPlan minimalPlan() {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(),
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
                "q",
                "q",
                "q",
                "q",
                "L",
                Optional.empty(),
                ClassifierStatus.DISABLED,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                entities,
                StructuredRewriteResult.identityDisabled("r", "r"),
                ExpectedAnswerShape.UNKNOWN,
                AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }
}
