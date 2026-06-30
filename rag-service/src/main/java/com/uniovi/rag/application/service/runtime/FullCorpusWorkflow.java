package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvoker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class FullCorpusWorkflow extends AbstractExecutionWorkflow {

    private final SnapshotCorpusAssembler snapshotCorpusAssembler;
    private final RuntimePromptBudgeter promptBudgeter;
    private final RuntimeAnswerPromptResolver answerPromptResolver;

    public FullCorpusWorkflow(
            RagLlmChatInvoker llmChatInvoker,
            SnapshotCorpusAssembler snapshotCorpusAssembler,
            RuntimePromptBudgeter promptBudgeter,
            @Autowired(required = false) RuntimeAnswerPromptResolver answerPromptResolver,
            @Autowired(required = false) ObservabilitySupport observability) {
        super(llmChatInvoker, observability);
        this.snapshotCorpusAssembler = snapshotCorpusAssembler;
        this.promptBudgeter = promptBudgeter;
        this.answerPromptResolver = answerPromptResolver;
    }

    @Override
    public RagExecutionResult execute(ExecutionContext ctx) {
        long t0 = System.nanoTime();
        List<ExecutionStageTrace> stages = new ArrayList<>();
        String corpus = snapshotCorpusAssembler.assembleFullCorpusText(ctx);
        stages.add(stage("full_corpus_assembly", t0, ExecutionStageOutcome.SUCCESS, ""));
        long t1 = System.nanoTime();
        String q = canonicalGenerationQuery(ctx);
        RagConfig rag = ctx.resolved().toRagConfig();
        AnswerGroundingPolicy policy = AnswerGroundingPolicySelector.from(rag);
        String answer;
        boolean docBound = RuntimeAnswerPrompts.requiresStrictDocumentGrounding(q);
        boolean abstention = false;
        String abstentionReason = "";
        String corpusSafe = corpus != null ? corpus : "";
        RuntimePromptBudgeter.BudgetResult budget = promptBudgeter != null
                ? promptBudgeter.budgetForFullCorpus(corpusSafe)
                : RuntimePromptBudgeter.truncate("full_corpus", corpusSafe, 20_000, "default_full_corpus_max_chars");
        corpusSafe = budget.textUsed();
        stages.add(stage(
                "context_budget",
                t1,
                ExecutionStageOutcome.SUCCESS,
                "stage=" + budget.stage()
                        + " truncated=" + budget.truncated()
                        + " originalChars=" + budget.originalChars()
                        + " finalChars=" + budget.finalChars()
                        + " budgetChars=" + budget.budgetChars()
                        + " reason=" + budget.reason()));
        if (corpusSafe.isBlank()) {
            answer =
                    answerPromptResolver != null
                            ? answerPromptResolver.insufficientDocumentContextMessage(ctx, q)
                            : RuntimeAnswerPrompts.insufficientDocumentContextMessageFor(q);
            abstention = true;
            abstentionReason = docBound ? "no_document_evidence" : "corpus_required_empty";
            stages.add(
                    stage(
                            "llm",
                            t1,
                            ExecutionStageOutcome.SKIPPED,
                            docBound ? "strict_document_grounding_no_context" : "corpus_required_no_document_evidence"));
        } else {
            String user =
                    answerPromptResolver != null
                            ? answerPromptResolver.ragUserTurn(
                                    ctx, q, corpusSafe, policy, docBound, Optional.empty(), answerPlanBlock(ctx))
                            : RuntimeAnswerPrompts.ragUserTurn(
                                    q, corpusSafe, policy, docBound, Optional.empty(), answerPlanBlock(ctx));
            answer = invokeChat(ctx, ctx.effectiveSystemPrompt(), user);
            stages.add(stage("llm", t1, ExecutionStageOutcome.SUCCESS, ""));
        }
        stages.add(
                RuntimeAnswerPrompts.runtimeAnswerMetaStage(
                        policy, corpusSafe.length(), 0, abstention, abstentionReason));
        return RagExecutionResult.withPlaceholderTrace(
                answer,
                workflowName(),
                true,
                false,
                ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                null,
                stages);
    }

    @Override
    public String workflowName() {
        return "FullCorpusWorkflow";
    }
}
