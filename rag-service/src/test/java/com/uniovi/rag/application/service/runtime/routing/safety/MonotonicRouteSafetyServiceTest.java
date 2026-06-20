package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonotonicRouteSafetyServiceTest {

    private MonotonicRouteSafetyService service;

    @BeforeEach
    void setUp() {
        service = new MonotonicRouteSafetyService(new RouteCandidateConstraintValidator());
    }

    @Test
    void selectSafest_prefersSafeRetrievalWhenAdvancedRejectedOnFilterList() {
        QueryPlan plan =
                plan(
                        "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.",
                        QueryType.FILTER_AND_LIST);
        MonotonicRouteSafetyService.CandidateScore retrieval =
                new MonotonicRouteSafetyService.CandidateScore(
                        "RETRIEVAL",
                        RouteCandidateValidationResult.accepted(0.88, "TOPIC_COVERED"),
                        "El acta del 24 de febrero de 2025 menciona el ascensor y fue presidida por Juan Pérez Gutiérrez.");

        Optional<MonotonicRouteSafetyService.CandidateScore> selected =
                service.selectSafest(
                        plan,
                        Optional.empty(),
                        Optional.empty(),
                        retrieval,
                        true);

        assertThat(selected).map(MonotonicRouteSafetyService.CandidateScore::source).contains("RETRIEVAL");
    }

    @Test
    void selectSafest_doesNotForceUnsafeRetrievalWhenAdvancedRejectedOnFilterList() {
        QueryPlan plan =
                plan(
                        "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.",
                        QueryType.FILTER_AND_LIST);
        MonotonicRouteSafetyService.CandidateScore retrieval =
                new MonotonicRouteSafetyService.CandidateScore(
                        "RETRIEVAL",
                        RouteCandidateValidationResult.rejected("function_sentinel_abstention"),
                        "TOPIC_NOT_IN_CONTEXT");

        Optional<MonotonicRouteSafetyService.CandidateScore> selected =
                service.selectSafest(
                        plan,
                        Optional.empty(),
                        Optional.empty(),
                        retrieval,
                        true);

        assertThat(selected).isEmpty();
    }

    @Test
    void selectSafest_allowsSafeFunctionWhenOnlyToolRejected() {
        QueryPlan plan =
                plan(
                        "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez.",
                        QueryType.FILTER_AND_LIST);
        MonotonicRouteSafetyService.CandidateScore function =
                new MonotonicRouteSafetyService.CandidateScore(
                        "FUNCTION",
                        RouteCandidateValidationResult.accepted(0.9, "TOPIC_COVERED"),
                        "El acta del 24 de febrero de 2025 menciona el ascensor y fue presidida por Juan Pérez Gutiérrez.");
        MonotonicRouteSafetyService.CandidateScore retrieval =
                new MonotonicRouteSafetyService.CandidateScore(
                        "RETRIEVAL",
                        RouteCandidateValidationResult.accepted(0.8, "TOPIC_COVERED"),
                        "partial retrieval");

        Optional<MonotonicRouteSafetyService.CandidateScore> selected =
                service.selectSafest(plan, Optional.empty(), Optional.of(function), retrieval, true);

        assertThat(selected).map(MonotonicRouteSafetyService.CandidateScore::source).contains("RETRIEVAL");
    }

    @Test
    void augmentFunctionRejectionWhenParentSupported_addsCanonicalAbstentionReason() {
        MonotonicSafetyTelemetry telemetry =
                MonotonicSafetyTelemetry.create()
                        .functionCandidateRejected(true)
                        .rejectCandidate("FUNCTION", "filter_list_unsupported_abstention,function_sentinel_abstention");

        telemetry.augmentFunctionRejectionWhenParentSupported(
                RouteCandidateValidationResult.rejected(
                        List.of("filter_list_unsupported_abstention", "function_sentinel_abstention")));

        assertThat(telemetry.candidateRejectionReasons().toString())
                .contains("function_abstention_despite_supported_parent");
    }

    @Test
    void augmentFunctionRejectionWhenRetrievalSupported_addsCanonicalAbstentionReason() {
        MonotonicSafetyTelemetry telemetry =
                MonotonicSafetyTelemetry.create()
                        .functionCandidateRejected(true)
                        .rejectCandidate("FUNCTION", "filter_list_unsupported_abstention,function_sentinel_abstention");

        telemetry.augmentFunctionRejectionWhenRetrievalSupported(
                RouteCandidateValidationResult.rejected(
                        List.of("filter_list_unsupported_abstention", "function_sentinel_abstention")));

        assertThat(telemetry.candidateRejectionReasons().toString())
                .contains("function_abstention_despite_supported_retrieval");
    }

    private static QueryPlan plan(String query, QueryType queryType) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                query,
                query,
                query,
                query,
                queryType.name(),
                Optional.of(queryType),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled(query, ""),
                ExpectedAnswerShape.UNKNOWN,
                new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                "corr",
                "",
                List.of());
    }
}
