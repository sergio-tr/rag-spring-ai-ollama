package com.uniovi.rag.application.service.runtime.clarification;

import com.uniovi.rag.application.port.PendingClarificationStore;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationExecutionResult;
import com.uniovi.rag.domain.runtime.clarification.PendingClarificationState;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Sole entry for clarification persistence mutators (P11).
 */
@Service
public class ClarificationStrategy {

    private final PendingClarificationStore pendingClarificationStore;

    public ClarificationStrategy(PendingClarificationStore pendingClarificationStore) {
        this.pendingClarificationStore = pendingClarificationStore;
    }

    public void clearInvalidPending(UUID conversationId) {
        pendingClarificationStore.clear(conversationId);
    }

    public void clearAfterResolved(UUID conversationId) {
        pendingClarificationStore.clear(conversationId);
    }

    public ClarificationExecutionResult executeAsk(
            ExecutionContext ctx, QueryPlan plan, ClarificationDecision decision) {
        Objects.requireNonNull(decision.questionIfAsking(), "questionIfAsking");
        var q = decision.questionIfAsking();
        UUID originating = ctx.originatingUserMessageId().orElseGet(UUID::randomUUID);
        PendingClarificationState state =
                new PendingClarificationState(
                        PendingClarificationState.SCHEMA_VERSION,
                        originating,
                        plan.rawUserQuery(),
                        q.questionText(),
                        q.questionKind(),
                        plan.ambiguityAssessment().missingFields(),
                        plan.ambiguityAssessment().reasons(),
                        Instant.now(),
                        ctx.correlationId());
        UUID conv = Objects.requireNonNull(ctx.conversationId(), "conversationId");
        pendingClarificationStore.saveReplace(conv, state);

        List<ExecutionStageTrace> stages =
                List.of(
                        new ExecutionStageTrace(
                                "clarification_question_generate",
                                0L,
                                ExecutionStageOutcome.SUCCESS,
                                "questionKind=" + q.questionKind().name()));

        return new ClarificationExecutionResult(decision.terminalOutcome(), q.questionText(), stages);
    }
}
