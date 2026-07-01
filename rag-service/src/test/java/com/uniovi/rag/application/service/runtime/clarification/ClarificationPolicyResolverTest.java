package com.uniovi.rag.application.service.runtime.clarification;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClarificationPolicyResolverTest {

    private final ClarificationPolicyResolver resolver =
            new ClarificationPolicyResolver(new ClarificationQuestionGenerator());

    @Test
    void resolve_invalidPendingRecovered() {
        ExecutionContext ctx = ctxBase("hi", true, false, false, Optional.empty());
        QueryPlan plan = plan(AmbiguityStatus.SUFFICIENT, List.of());
        ClarificationDecision d = resolver.resolve(ctx, plan);
        assertThat(d.terminalOutcome()).isEqualTo(ClarificationOutcome.INVALID_PENDING_STATE_RECOVERED);
        assertThat(d.ask()).isFalse();
    }

    @Test
    void resolve_disabledByConfig_whenDisableReasonPresent() {
        ExecutionContext ctx =
                ctxBase("hi", false, false, false, Optional.of("config_disabled"));
        QueryPlan plan = plan(AmbiguityStatus.MISSING_INFORMATION, List.of("topic"));
        ClarificationDecision d = resolver.resolve(ctx, plan);
        assertThat(d.terminalOutcome()).isEqualTo(ClarificationOutcome.DISABLED_BY_CONFIG);
        assertThat(d.policyTraceNote()).contains("disable_reason=config_disabled");
        assertThat(d.ask()).isFalse();
    }

    @Test
    void resolve_resolvedFromPending_whenSufficientAndMerged() {
        ExecutionContext ctx = ctxBase("answer", false, true, true, Optional.empty());
        QueryPlan plan = plan(AmbiguityStatus.SUFFICIENT, List.of());
        ClarificationDecision d = resolver.resolve(ctx, plan);
        assertThat(d.terminalOutcome()).isEqualTo(ClarificationOutcome.RESOLVED_FROM_PENDING);
        assertThat(d.ask()).isFalse();
    }

    @Test
    void resolve_notNeeded_whenAmbiguityDoesNotRequireAsk() {
        ExecutionContext ctx = ctxBase("hi", false, false, false, Optional.empty());
        QueryPlan plan = plan(AmbiguityStatus.UNKNOWN, List.of());
        ClarificationDecision d = resolver.resolve(ctx, plan);
        assertThat(d.terminalOutcome()).isEqualTo(ClarificationOutcome.NOT_NEEDED);
        assertThat(d.ask()).isFalse();
    }

    @Test
    void resolve_selectsMissingDateKind_fromMissingFields() {
        ExecutionContext ctx = ctxBase("hi", false, false, false, Optional.empty());
        QueryPlan plan = plan(AmbiguityStatus.MISSING_INFORMATION, List.of("Meeting deadline"));
        ClarificationDecision d = resolver.resolve(ctx, plan);
        assertThat(d.ask()).isTrue();
        assertThat(d.terminalOutcome()).isEqualTo(ClarificationOutcome.ASKED_CLARIFICATION);
        assertThat(d.questionIfAsking().questionKind()).isEqualTo(ClarificationQuestionKind.MISSING_DATE);
    }

    @Test
    void resolve_askedAgain_whenValidPendingExistedAtLoad() {
        ExecutionContext ctx = ctxBase("hi", false, true, true, Optional.empty());
        QueryPlan plan = plan(AmbiguityStatus.MISSING_INFORMATION, List.of("topic"));
        ClarificationDecision d = resolver.resolve(ctx, plan);
        assertThat(d.ask()).isTrue();
        assertThat(d.terminalOutcome()).isEqualTo(ClarificationOutcome.ASKED_CLARIFICATION_AGAIN);
    }

    @Test
    void resolve_notNeeded_forConflictingCues() {
        ExecutionContext ctx = ctxBase("hi", false, false, false, Optional.empty());
        QueryPlan plan = plan(AmbiguityStatus.CONFLICTING_CUES, List.of());
        ClarificationDecision d = resolver.resolve(ctx, plan);
        assertThat(d.ask()).isFalse();
        assertThat(d.terminalOutcome()).isEqualTo(ClarificationOutcome.NOT_NEEDED);
    }

    @Test
    void resolve_cl01_ambiguousParticipantCount_asksForDate() {
        ExecutionContext ctx = ctxBase("¿Cuántos participantes asistieron?", false, false, false, Optional.empty());
        QueryPlan plan =
                plan(
                        AmbiguityStatus.MISSING_INFORMATION,
                        List.of("Meeting date", "time_reference"));
        ClarificationDecision d = resolver.resolve(ctx, plan);
        assertThat(d.ask()).isTrue();
        assertThat(d.terminalOutcome()).isEqualTo(ClarificationOutcome.ASKED_CLARIFICATION);
        assertThat(d.questionIfAsking().questionKind()).isEqualTo(ClarificationQuestionKind.MISSING_DATE);
    }

    @Test
    void resolve_notNeeded_forFdFl03CompoundMonthTopicFilter() {
        ExecutionContext ctx = ctxBase("hi", false, false, false, Optional.empty());
        QueryPlan plan =
                new QueryPlan(
                        QueryPlan.VERSION_P11_QU_CLARIFICATION_CORE_V1,
                        "raw",
                        "raw",
                        "¿Qué reuniones celebradas en agosto hablaron sobre videovigilancia y tuvieron más de 18 asistentes?",
                        "rw",
                        "lbl",
                        Optional.empty(),
                        ClassifierStatus.OK,
                        QueryIntent.UNKNOWN,
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        StructuredRewriteResult.identityDisabled("norm", ""),
                        ExpectedAnswerShape.UNKNOWN,
                        new AmbiguityAssessment(
                                AmbiguityStatus.MISSING_INFORMATION,
                                List.of(),
                                List.of("time_reference")),
                        "c",
                        "",
                        List.of());
        ClarificationDecision d = resolver.resolve(ctx, plan);
        assertThat(d.ask()).isFalse();
        assertThat(d.terminalOutcome()).isEqualTo(ClarificationOutcome.NOT_NEEDED);
        assertThat(d.policyTraceNote()).contains("compound_month_topic_attendee_filter");
    }

    @Test
    void resolve_notNeeded_forFdCe02ExactAttendeeListing() {
        ExecutionContext ctx = ctxBase("hi", false, false, false, Optional.empty());
        String q =
                "¿En qué reuniones hubo exactamente 21 asistentes y qué se decidió en esa reunión?";
        QueryPlan plan =
                new QueryPlan(
                        QueryPlan.VERSION_P11_QU_CLARIFICATION_CORE_V1,
                        "raw",
                        "raw",
                        q,
                        "rw",
                        "lbl",
                        Optional.of(QueryType.COUNT_AND_EXPLAIN),
                        ClassifierStatus.OK,
                        QueryIntent.UNKNOWN,
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        StructuredRewriteResult.identityDisabled("norm", ""),
                        ExpectedAnswerShape.UNKNOWN,
                        new AmbiguityAssessment(
                                AmbiguityStatus.MISSING_INFORMATION,
                                List.of(),
                                List.of("time_reference")),
                        "c",
                        "",
                        List.of());
        ClarificationDecision d = resolver.resolve(ctx, plan);
        assertThat(d.ask()).isFalse();
        assertThat(d.terminalOutcome()).isEqualTo(ClarificationOutcome.NOT_NEEDED);
        assertThat(d.policyTraceNote()).contains("corpus_wide_exact_attendee_count_listing");
    }

    @Test
    void resolve_notNeeded_forCorpusWideListing() {
        ExecutionContext ctx = ctxBase("dime qué actas tienen 20 asistentes", false, false, false, Optional.empty());
        QueryPlan plan =
                new QueryPlan(
                        QueryPlan.VERSION_P11_QU_CLARIFICATION_CORE_V1,
                        "raw",
                        "raw",
                        "dime qué actas tienen 20 asistentes",
                        "rw",
                        "lbl",
                        Optional.of(QueryType.FILTER_AND_LIST),
                        ClassifierStatus.OK,
                        QueryIntent.UNKNOWN,
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        StructuredRewriteResult.identityDisabled("norm", ""),
                        ExpectedAnswerShape.UNKNOWN,
                        new AmbiguityAssessment(
                                AmbiguityStatus.MISSING_INFORMATION,
                                List.of(),
                                List.of("time_reference")),
                        "c",
                        "",
                        List.of());
        ClarificationDecision d = resolver.resolve(ctx, plan);
        assertThat(d.ask()).isFalse();
        assertThat(d.policyTraceNote()).contains("corpus_wide_listing_exempt");
    }

    @Test
    void selectKind_conflictStatus_selectsConflictingCuesKind() {
        QueryPlan plan = plan(AmbiguityStatus.CONFLICTING_CUES, List.of());
        assertThat(resolver.selectKind(plan))
                .contains(ClarificationQuestionKind.CONFLICTING_CUES);
    }

    private static ExecutionContext ctxBase(
            String userQuery,
            boolean invalidRecovered,
            boolean pendingLoaded,
            boolean validPendingAtLoad,
            Optional<String> disableReason) {
        RagConfig rag = rag();
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        UUID id = UUID.randomUUID();
        return new ExecutionContext(
                id,
                id,
                id,
                userQuery,
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "c",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                userQuery,
                userQuery,
                Optional.empty(),
                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                List.of(),
                false,
                false,
                false,
                false,
                false,
                pendingLoaded,
                validPendingAtLoad,
                invalidRecovered,
                disableReason,
                Optional.empty(),
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }

    private static RagConfig rag() {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                false,
                false,
                5,
                0.2,
                "l",
                "e",
                "c",
                "r",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                MaterializationStrategy.CHUNK_LEVEL);
    }

    @Test
    void resumenSlashDate_noClarification() {
        ExecutionContext ctx = ctxBase("hazme un resumen del 25/02/26", false, false, false, Optional.empty());
        QueryPlan plan =
                new QueryPlan(
                        QueryPlan.VERSION_P11_QU_CLARIFICATION_CORE_V1,
                        "hazme un resumen del 25/02/26",
                        "hazme un resumen del 25/02/26",
                        "hazme un resumen del 25/02/26",
                        "hazme un resumen del 25/02/26",
                        "lbl",
                        Optional.of(QueryType.SUMMARIZE_MEETING),
                        ClassifierStatus.OK,
                        QueryIntent.UNKNOWN,
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote(""),
                        StructuredRewriteResult.identityDisabled("norm", ""),
                        ExpectedAnswerShape.UNKNOWN,
                        AmbiguityAssessment.sufficient(),
                        "c",
                        "",
                        List.of());
        ClarificationDecision d = resolver.resolve(ctx, plan);
        assertThat(d.ask()).isFalse();
        assertThat(d.terminalOutcome()).isEqualTo(ClarificationOutcome.NOT_NEEDED);
    }

    private static QueryPlan plan(AmbiguityStatus status, List<String> missing) {
        return new QueryPlan(
                QueryPlan.VERSION_P11_QU_CLARIFICATION_CORE_V1,
                "raw",
                "raw",
                "norm",
                "rw",
                "lbl",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled("norm", ""),
                ExpectedAnswerShape.UNKNOWN,
                new AmbiguityAssessment(status, List.of(), missing),
                "c",
                "",
                List.of());
    }
}
