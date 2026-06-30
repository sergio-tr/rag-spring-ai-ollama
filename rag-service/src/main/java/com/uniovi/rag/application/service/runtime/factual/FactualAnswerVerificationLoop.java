package com.uniovi.rag.application.service.runtime.factual;

import com.uniovi.rag.application.service.runtime.RuntimeAnswerPrompts;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.factual.FinalAnswerSource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class FactualAnswerVerificationLoop {

    private FactualAnswerVerificationLoop() {}

    public record Outcome(
            String answerText,
            boolean abstentionTriggered,
            String abstentionReason,
            FinalAnswerSource finalAnswerSource,
            List<ExecutionStageTrace> stages) {}

    public static Outcome apply(
            String question,
            FactualQuestionConstraints constraints,
            String contextText,
            String draftAnswer,
            Function<String, String> revisionInvoker) {
        return apply(question, constraints, contextText, draftAnswer, revisionInvoker, FactualRevisionPrompts.defaultRevisionTemplate());
    }

    public static Outcome apply(
            String question,
            FactualQuestionConstraints constraints,
            String contextText,
            String draftAnswer,
            Function<String, String> revisionInvoker,
            String revisionTemplate) {
        return apply(
                question,
                constraints,
                contextText,
                draftAnswer,
                revisionInvoker,
                revisionTemplate,
                RuntimeAnswerPrompts.insufficientDocumentContextMessageFor(question));
    }

    public static Outcome apply(
            String question,
            FactualQuestionConstraints constraints,
            String contextText,
            String draftAnswer,
            Function<String, String> revisionInvoker,
            String revisionTemplate,
            String abstentionMessage) {
        List<ExecutionStageTrace> stages = new ArrayList<>();
        if (contextText == null || contextText.isBlank() || draftAnswer == null || draftAnswer.isBlank()) {
            if (contextText == null || contextText.isBlank()) {
                stages.add(
                        new ExecutionStageTrace(
                                FactualVerifierTelemetry.STAGE_VERIFY_SKIPPED,
                                0L,
                                ExecutionStageOutcome.SKIPPED,
                                FactualVerifierTelemetry.formatSkippedMessage("no_retrieved_context")));
            }
            return new Outcome(
                    draftAnswer != null ? draftAnswer : "",
                    false,
                    "",
                    FinalAnswerSource.GENERATED,
                    stages);
        }

        FactualVerifierResult first = FactualAnswerVerifier.verify(constraints, contextText, draftAnswer);
        stages.add(stage("factual_verify", first.passed() ? ExecutionStageOutcome.SUCCESS : ExecutionStageOutcome.FAILED, first));
        if (first.passed()) {
            stages.add(stage("factual_verify_final", ExecutionStageOutcome.SUCCESS, first, false));
            return new Outcome(draftAnswer, false, "", FinalAnswerSource.GENERATED, stages);
        }

        stages.add(new ExecutionStageTrace("factual_verify_revision", 0L, ExecutionStageOutcome.SUCCESS, "attempted=true"));
        String revisionPrompt =
                FactualRevisionPrompts.revisionUserTurn(revisionTemplate, question, contextText, draftAnswer, first);
        String revised = revisionInvoker.apply(revisionPrompt);
        FactualVerifierResult second = FactualAnswerVerifier.verify(constraints, contextText, revised);
        stages.add(stage("factual_verify", second.passed() ? ExecutionStageOutcome.SUCCESS : ExecutionStageOutcome.FAILED, second));

        if (second.passed()) {
            stages.add(stage("factual_verify_final", ExecutionStageOutcome.SUCCESS, second, true));
            return new Outcome(revised, false, "", FinalAnswerSource.GENERATED, stages);
        }

        String abstain =
                abstentionMessage != null && !abstentionMessage.isBlank()
                        ? abstentionMessage
                        : RuntimeAnswerPrompts.insufficientDocumentContextMessageFor(question);
        stages.add(stage("factual_verify_final", ExecutionStageOutcome.FAILED, second, true));
        return new Outcome(abstain, true, "factual_verifier_forced_abstention", FinalAnswerSource.FORCED_ABSTENTION, stages);
    }

    private static ExecutionStageTrace stage(
            String name, ExecutionStageOutcome outcome, FactualVerifierResult result) {
        return stage(name, outcome, result, false);
    }

    private static ExecutionStageTrace stage(
            String name,
            ExecutionStageOutcome outcome,
            FactualVerifierResult result,
            boolean revisionAttempted) {
        return new ExecutionStageTrace(
                name,
                0L,
                outcome,
                FactualVerifierTelemetry.formatMessage(result, revisionAttempted, outcome == ExecutionStageOutcome.FAILED && name.equals("factual_verify_final")));
    }
}
