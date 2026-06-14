package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.factual.FactualAnswerVerificationLoop;
import com.uniovi.rag.application.service.runtime.factual.FactualConstraintExtractor;
import com.uniovi.rag.application.service.runtime.factual.FactualQuestionConstraints;
import com.uniovi.rag.application.service.runtime.factual.FactualVerifierTelemetry;
import com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.factual.FinalAnswerSource;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.CuratedContextSet;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

abstract class AbstractDenseRagWorkflow extends AbstractExecutionWorkflow {

    private final AdvancedRetrievalPipeline advancedRetrievalPipeline;

    protected AbstractDenseRagWorkflow(
            ChatClient chatClient,
            AdvancedRetrievalPipeline advancedRetrievalPipeline,
            @Autowired(required = false) ObservabilitySupport observability) {
        super(chatClient, observability);
        this.advancedRetrievalPipeline = advancedRetrievalPipeline;
    }

    protected boolean metadataUsedInResult() {
        return false;
    }

    @Override
    public RagExecutionResult execute(ExecutionContext ctx) {
        QueryPlan plan = ctx.queryPlan().orElseThrow(() -> new IllegalStateException("QueryPlan required"));
        String q = canonicalGenerationQuery(ctx);
        long tLlm = System.nanoTime();
        RagConfig rag = ctx.resolved().toRagConfig();
        FactualQuestionConstraints constraints =
                FactualConstraintExtractor.extract(q, plan, plan.classifierQueryType());
        AnswerGroundingPolicy policy = constraints.groundingPolicy();
        String constraintsBlock = FactualConstraintExtractor.constraintsPromptBlock(constraints);
        String combinedPlan = combinePlanBlocks(answerPlanBlock(ctx), constraintsBlock);

        Optional<PackedContextSet> packed = ctx.advisorPackedContextSet();
        if (packed.isPresent()) {
            return executePacked(ctx, q, tLlm, policy, combinedPlan, constraints, packed.get());
        }

        CuratedContextSet curated = advancedRetrievalPipeline.retrieve(ctx, plan, workflowName());
        List<ExecutionStageTrace> stages = new ArrayList<>(curated.retrievalStageTraces());
        String rawPromptContext = curated.promptContextText();
        boolean docBound = RuntimeAnswerPrompts.requiresStrictDocumentGrounding(q);
        boolean dateGroundingActive =
                docBound
                        || DateGroundingSupport.requestedDate(q, plan.entityExtractionResult().dates())
                                .isPresent();
        DateGroundingSupport.DateGroundingDecision dateDecision =
                dateGroundingActive
                        ? DateGroundingSupport.decision(q, plan.entityExtractionResult().dates(), curated.finalCandidates())
                        : DateGroundingSupport.decision("", curated.finalCandidates());
        String effectivePromptContext =
                RuntimeAnswerPrompts.effectivePromptContextForDateGrounding(
                        rawPromptContext, curated.finalCandidates(), dateDecision);
        stages.add(new ExecutionStageTrace(
                "packed_context_preview",
                0L,
                ExecutionStageOutcome.SUCCESS,
                "preview=" + preview(effectivePromptContext)));
        stages.add(new ExecutionStageTrace(
                "date_grounding_answer_policy",
                0L,
                ExecutionStageOutcome.SUCCESS,
                DateGroundingSupport.traceMessage(dateDecision)));
        Optional<String> mismatch =
                dateGroundingActive && dateDecision.dateMismatchDetected()
                        ? Optional.of(DateGroundingSupport.mismatchMessage(q, dateDecision))
                        : Optional.empty();

        String answer;
        boolean abstention = false;
        String abstentionReason = "";
        FinalAnswerSource finalAnswerSource = FinalAnswerSource.GENERATED;

        if (docBound && effectivePromptContext.isBlank()) {
            answer = RuntimeAnswerPrompts.insufficientDocumentContextMessageFor(q);
            abstention = true;
            abstentionReason = "no_document_evidence";
            finalAnswerSource = FinalAnswerSource.FORCED_ABSTENTION;
            stages.add(stage("llm", tLlm, ExecutionStageOutcome.SKIPPED, "strict_document_grounding_no_context"));
        } else if (mismatch.isPresent()) {
            answer = mismatch.get();
            abstention = true;
            abstentionReason = dateDecision.abstentionReason().isBlank() ? "date_mismatch_no_exact_source" : dateDecision.abstentionReason();
            finalAnswerSource = FinalAnswerSource.DATE_GUARD_ABSTENTION;
            stages.add(stage("llm", tLlm, ExecutionStageOutcome.SKIPPED, "date_mismatch_no_exact_source"));
            stages.add(new ExecutionStageTrace(
                    FactualVerifierTelemetry.STAGE_VERIFY_SKIPPED,
                    0L,
                    ExecutionStageOutcome.SKIPPED,
                    FactualVerifierTelemetry.formatSkippedMessage("date_guard_abstention")));
        } else {
            String user =
                    RuntimeAnswerPrompts.ragUserTurn(
                            q, effectivePromptContext, policy, docBound, mismatch, combinedPlan);
            String draft = invokeChat(ctx, ctx.effectiveSystemPrompt(), user);
            stages.add(stage("llm", tLlm, ExecutionStageOutcome.SUCCESS, ""));
            FactualAnswerVerificationLoop.Outcome verified =
                    FactualAnswerVerificationLoop.apply(
                            q, constraints, effectivePromptContext, draft, revisionPrompt -> invokeChat(ctx, ctx.effectiveSystemPrompt(), revisionPrompt));
            answer = verified.answerText();
            abstention = verified.abstentionTriggered();
            abstentionReason = verified.abstentionReason();
            finalAnswerSource = verified.finalAnswerSource();
            stages.addAll(verified.stages());
        }

        stages.add(finalAnswerSourceStage(finalAnswerSource));
        stages.add(
                RuntimeAnswerPrompts.runtimeAnswerMetaStage(
                        policy,
                        effectivePromptContext.length(),
                        curated.finalCandidates().size(),
                        abstention,
                        abstentionReason,
                        docBound));

        Optional<DateGroundingSupport.RequestedDate> requestedForSources =
                dateDecision.requestedDate() != null ? Optional.of(dateDecision.requestedDate()) : Optional.empty();

        return RagExecutionResult.withPlaceholderTrace(
                        answer,
                        workflowName(),
                        true,
                        metadataUsedInResult(),
                        ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                        null,
                        Optional.ofNullable(curated.diagnostics()),
                        stages)
                .withResponseSources(
                        ChatSourceMapper.toPersistedMapsFromInternal(
                                RuntimeRetrievedSourceMapper.toChatSources(
                                        curated.finalCandidates(), requestedForSources, dateDecision)));
    }

    private RagExecutionResult executePacked(
            ExecutionContext ctx,
            String q,
            long tLlm,
            AnswerGroundingPolicy policy,
            String combinedPlan,
            FactualQuestionConstraints constraints,
            PackedContextSet packed) {
        String context = packed.promptContextText();
        String user =
                RuntimeAnswerPrompts.ragUserTurn(
                        q,
                        context,
                        policy,
                        RuntimeAnswerPrompts.requiresStrictDocumentGrounding(q),
                        Optional.empty(),
                        combinedPlan);
        String draft = invokeChat(ctx, ctx.effectiveSystemPrompt(), user);
        List<ExecutionStageTrace> stages = new ArrayList<>();
        stages.add(stage("llm", tLlm, ExecutionStageOutcome.SUCCESS, "from_advisor_packed_context"));
        FactualAnswerVerificationLoop.Outcome verified =
                FactualAnswerVerificationLoop.apply(
                        q, constraints, context, draft, revisionPrompt -> invokeChat(ctx, ctx.effectiveSystemPrompt(), revisionPrompt));
        stages.addAll(verified.stages());
        stages.add(finalAnswerSourceStage(verified.finalAnswerSource()));
        boolean packedDocBound = RuntimeAnswerPrompts.requiresStrictDocumentGrounding(q);
        stages.add(
                RuntimeAnswerPrompts.runtimeAnswerMetaStage(
                        policy,
                        context != null ? context.length() : 0,
                        packed.totalSourceCount(),
                        verified.abstentionTriggered(),
                        verified.abstentionReason(),
                        packedDocBound));
        return RagExecutionResult.withPlaceholderTrace(
                        verified.answerText(),
                        workflowName(),
                        true,
                        metadataUsedInResult(),
                        ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                        null,
                        Optional.empty(),
                        stages)
                .withResponseSources(List.of());
    }

    private static ExecutionStageTrace finalAnswerSourceStage(FinalAnswerSource source) {
        return new ExecutionStageTrace(
                "final_answer_source",
                0L,
                ExecutionStageOutcome.SUCCESS,
                "finalAnswerSource=" + (source != null ? source.name() : FinalAnswerSource.GENERATED.name()));
    }

    private static String combinePlanBlocks(String answerPlanBlock, String constraintsBlock) {
        String plan = answerPlanBlock != null && !answerPlanBlock.isBlank() ? answerPlanBlock.trim() : "";
        String constraints = constraintsBlock != null && !constraintsBlock.isBlank() ? constraintsBlock.trim() : "";
        if (plan.isBlank()) {
            return constraints;
        }
        if (constraints.isBlank()) {
            return plan;
        }
        return plan + "\n" + constraints;
    }

    private static String preview(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        int max = 360;
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max);
    }
}
