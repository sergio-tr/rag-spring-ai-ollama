package com.uniovi.rag.application.service.runtime.clarification;

import com.uniovi.rag.application.port.PendingClarificationStore;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationExecutionResult;
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestion;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
import com.uniovi.rag.domain.runtime.clarification.PendingClarificationState;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClarificationStrategyTest {

    @Mock
    private PendingClarificationStore pendingClarificationStore;

    @Test
    void executeAsk_persistsReplace_andReturnsQuestionText() {
        UUID conv = UUID.randomUUID();
        UUID msg = UUID.randomUUID();
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
        ExecutionContext ctx =
                new ExecutionContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        conv,
                        "ambiguous question",
                        RuntimeOperationKind.CHAT_MESSAGE,
                        resolved,
                        "sys",
                        KnowledgeSnapshotSelection.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        "corr",
                        List.of("all"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        "ambiguous question",
                        "ambiguous question",
                        Optional.empty(),
                        ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                        List.of(),
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        Optional.empty(),
                        Optional.of(msg),
                        false,
                        AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        false,
                        Optional.empty(),
                        false,
                        List.of());
        QueryPlan plan =
                new QueryPlan(
                        QueryPlan.VERSION_P11_QU_CLARIFICATION_CORE_V1,
                        "ambiguous question",
                        "ambiguous question",
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
                        new AmbiguityAssessment(
                                AmbiguityStatus.MISSING_INFORMATION, List.of(), List.of("topic")),
                        "corr",
                        "",
                        List.of());
        ClarificationQuestion q =
                new ClarificationQuestion(
                        ClarificationQuestionKind.MISSING_TOPIC.templateText(),
                        ClarificationQuestionKind.MISSING_TOPIC,
                        List.of("topic"));
        ClarificationDecision decision =
                new ClarificationDecision(true, ClarificationOutcome.ASKED_CLARIFICATION, q, "");

        ClarificationStrategy strategy = new ClarificationStrategy(pendingClarificationStore);
        ClarificationExecutionResult result = strategy.executeAsk(ctx, plan, decision);

        assertThat(result.answerText()).isEqualTo(q.questionText());
        assertThat(result.outcome()).isEqualTo(ClarificationOutcome.ASKED_CLARIFICATION);
        ArgumentCaptor<PendingClarificationState> cap = ArgumentCaptor.forClass(PendingClarificationState.class);
        verify(pendingClarificationStore).saveReplace(eq(conv), cap.capture());
        assertThat(cap.getValue().originatingUserMessageId()).isEqualTo(msg);
        assertThat(cap.getValue().questionKind()).isEqualTo(ClarificationQuestionKind.MISSING_TOPIC);
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
}
