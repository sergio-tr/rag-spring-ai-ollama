package com.uniovi.rag.application.service.runtime.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingDecision;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicToolRoutingPolicyTest {

    private final DeterministicToolRoutingPolicy policy = new DeterministicToolRoutingPolicy();

    @Test
    void structuredCountRoutesToDeterministicTools() {
        QueryPlan plan =
                planWithClassifier(
                        "¿Cuántas reuniones hubo en 2025?",
                        QueryType.COUNT_DOCUMENTS,
                        Map.of());
        AdaptiveRoutingDecision decision = policy.resolve(ragWithTools(), plan);

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);
        assertThat(decision.reasons()).anyMatch(r -> r.contains("structured_classifier_field_route"));
    }

    @Test
    void explicitActaFilenameFieldQuery_doesNotForceStructuredToolRoute() {
        QueryPlan plan =
                planWithClassifier(
                        "¿Cuántos propietarios asistieron a la reunión del 25 de agosto de 2025 (ACTA 3.pdf)?",
                        QueryType.GET_FIELD,
                        Map.of("field", "attendeesCount"));

        AdaptiveRoutingDecision decision = policy.resolve(ragWithTools(), plan);

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.RETRIEVAL_WORKFLOW_ROUTE);
        assertThat(decision.reasons()).anyMatch(r -> r.contains("explicit_acta_filename_field_query"));
    }

    @Test
    void structuredSummaryRoutesToDeterministicTools() {
        QueryPlan plan =
                planWithClassifier(
                        "Resume el acta del 25/02/2026.",
                        QueryType.SUMMARIZE_MEETING,
                        Map.of());
        AdaptiveRoutingDecision decision = policy.resolve(ragWithTools(), plan);

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);
    }

    @Test
    void structuredStartTimeListRoutesToDeterministicTools() {
        QueryPlan plan =
                planWithClassifier(
                        "¿Qué actas tienen hora de inicio a las 19:00?",
                        QueryType.FILTER_AND_LIST,
                        Map.of());
        AdaptiveRoutingDecision decision = policy.resolve(ragWithTools(), plan);

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);
    }

    @Test
    void structuredPersonRoleRoutesToDeterministicTools() {
        QueryPlan plan =
                planWithClassifier(
                        "¿Qué papel tuvo Jorge en la reunión del 25/08/2026?",
                        QueryType.GET_FIELD,
                        Map.of("field", "role"));
        AdaptiveRoutingDecision decision = policy.resolve(ragWithTools(), plan);

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);
    }

    @Test
    void undatedParticipantCount_doesNotForceStructuredCountRoute() {
        QueryPlan plan =
                new QueryPlan(
                        QueryPlan.VERSION_P6_QU_CORE_V1,
                        "¿Cuántos participantes asistieron?",
                        "¿Cuántos participantes asistieron?",
                        "¿Cuántos participantes asistieron?",
                        "¿Cuántos participantes asistieron?",
                        "COUNT_DOCUMENTS",
                        Optional.of(QueryType.COUNT_DOCUMENTS),
                        ClassifierStatus.OK,
                        QueryIntent.COUNT,
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(null),
                        StructuredRewriteResult.identityFallback("¿Cuántos participantes asistieron?", null),
                        ExpectedAnswerShape.SCALAR_COUNT,
                        new AmbiguityAssessment(
                                AmbiguityStatus.MISSING_INFORMATION,
                                List.of("Missing acta/meeting date for scoped field lookup"),
                                List.of("time_reference")),
                        "corr",
                        "default",
                        List.of());

        AdaptiveRoutingDecision decision = policy.resolve(ragWithTools(), plan);
        assertThat(decision.primaryRouteKind()).isNotEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);
    }

    @Test
    void fdFl03_augustVideovigilanciaFilter_routesToDeterministicTools() {
        QueryPlan plan =
                planWithClassifier(
                        "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                        QueryType.FILTER_AND_LIST,
                        Map.of());
        AdaptiveRoutingDecision decision = policy.resolve(ragWithTools(), plan);

        assertThat(decision.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);
        assertThat(decision.reasons()).anyMatch(r -> r.contains("structured_classifier_field_route"));
    }

    @Test
    void ambiguousPresidentWithoutDateDoesNotForceStructuredRoute() {
        QueryPlan plan =
                new QueryPlan(
                        QueryPlan.VERSION_P6_QU_CORE_V1,
                        "¿Quién fue el presidente?",
                        "¿Quién fue el presidente?",
                        "¿Quién fue el presidente?",
                        "¿Quién fue el presidente?",
                        "UNCLASSIFIED",
                        Optional.empty(),
                        ClassifierStatus.LOW_CONFIDENCE,
                        QueryIntent.UNKNOWN,
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(null),
                        StructuredRewriteResult.identityFallback("¿Quién fue el presidente?", null),
                        ExpectedAnswerShape.UNKNOWN,
                        new AmbiguityAssessment(
                                AmbiguityStatus.MISSING_INFORMATION, List.of("Missing date"), List.of("time_reference")),
                        "corr",
                        "default",
                        List.of());

        AdaptiveRoutingDecision decision = policy.resolve(ragWithTools(), plan);
        assertThat(decision.primaryRouteKind()).isNotEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);
        assertThat(decision.reasons())
                .anyMatch(r -> r.contains("ambiguity_not_sufficient") || r.contains("functionCallingEnabled=true"));
    }

    private static RagConfig ragWithTools() {
        return new RagConfig(
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                5,
                0.2,
                "llm",
                "emb",
                "cls",
                "reason",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                false,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    private static QueryPlan planWithClassifier(String q, QueryType type, Map<String, String> slots) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                q,
                q,
                q,
                q,
                type.name(),
                Optional.of(type),
                ClassifierStatus.OK,
                QueryIntent.COUNT,
                slots,
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(null),
                StructuredRewriteResult.identityFallback(q, null),
                ExpectedAnswerShape.SCALAR_COUNT,
                AmbiguityAssessment.sufficient(),
                "corr",
                "default",
                List.of());
    }
}
