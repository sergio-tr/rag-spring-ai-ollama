package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.CuratedContextSet;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared implementation for dense workflows that differ only by {@link #workflowName()} and {@link #metadataUsedInResult()}.
 */
abstract class AbstractDenseRagWorkflow extends AbstractExecutionWorkflow {

    private final AdvancedRetrievalPipeline advancedRetrievalPipeline;

    protected AbstractDenseRagWorkflow(
            ChatClient chatClient,
            AdvancedRetrievalPipeline advancedRetrievalPipeline,
            @Autowired(required = false) ObservabilitySupport observability) {
        super(chatClient, observability);
        this.advancedRetrievalPipeline = advancedRetrievalPipeline;
    }

    /** When {@code true}, trace marks metadata-assisted retrieval as used (metadata workflows). */
    protected boolean metadataUsedInResult() {
        return false;
    }

    @Override
    public RagExecutionResult execute(ExecutionContext ctx) {
        QueryPlan plan = ctx.queryPlan().orElseThrow(() -> new IllegalStateException("QueryPlan required"));
        String q = canonicalGenerationQuery(ctx);
        long tLlm = System.nanoTime();
        RagConfig rag = ctx.resolved().toRagConfig();
        AnswerGroundingPolicy policy = AnswerGroundingPolicySelector.from(rag);

        Optional<PackedContextSet> packed = ctx.advisorPackedContextSet();
        if (packed.isPresent()) {
            String user =
                    RuntimeAnswerPrompts.ragUserTurn(
                            q,
                            packed.get().promptContextText(),
                            policy,
                            RuntimeAnswerPrompts.requiresStrictDocumentGrounding(q),
                            Optional.empty(),
                            answerPlanBlock(ctx));
            String answer = invokeChat(ctx, ctx.effectiveSystemPrompt(), user);
            List<ExecutionStageTrace> stages = new ArrayList<>();
            stages.add(stage("llm", tLlm, ExecutionStageOutcome.SUCCESS, "from_advisor_packed_context"));
            stages.add(
                    RuntimeAnswerPrompts.runtimeAnswerMetaStage(
                            policy,
                            packed.get().promptContextText() != null ? packed.get().promptContextText().length() : 0,
                            packed.get().totalSourceCount(),
                            false,
                            ""));
            return RagExecutionResult.withPlaceholderTrace(
                            answer,
                            workflowName(),
                            true,
                            metadataUsedInResult(),
                            ctx.knowledgeSnapshotSelection().orderedSnapshotIds(),
                            null,
                            Optional.empty(),
                            stages)
                    .withResponseSources(List.of());
        }

        CuratedContextSet curated = advancedRetrievalPipeline.retrieve(ctx, plan, workflowName());
        List<ExecutionStageTrace> stages = new ArrayList<>(curated.retrievalStageTraces());
        String rawPromptContext = curated.promptContextText();
        String effectivePromptContext =
                RuntimeAnswerPrompts.effectivePromptContext(rawPromptContext, curated.finalCandidates());
        stages.add(new ExecutionStageTrace(
                "packed_context_preview",
                0L,
                ExecutionStageOutcome.SUCCESS,
                "preview=" + preview(effectivePromptContext)));

        boolean docBound = RuntimeAnswerPrompts.requiresStrictDocumentGrounding(q);
        Optional<String> mismatch =
                docBound ? RuntimeAnswerPrompts.groundedDateMismatchMessageFor(q, curated.finalCandidates()) : Optional.empty();

        String answer;
        boolean abstention = false;
        String abstentionReason = "";

        if (docBound && effectivePromptContext.isBlank()) {
            answer = RuntimeAnswerPrompts.insufficientDocumentContextMessageFor(q);
            abstention = true;
            abstentionReason = "no_document_evidence";
            stages.add(stage("llm", tLlm, ExecutionStageOutcome.SKIPPED, "strict_document_grounding_no_context"));
        } else {
            String user =
                    RuntimeAnswerPrompts.ragUserTurn(
                            q, effectivePromptContext, policy, docBound, mismatch, answerPlanBlock(ctx));
            answer = invokeChat(ctx, ctx.effectiveSystemPrompt(), user);
            stages.add(stage("llm", tLlm, ExecutionStageOutcome.SUCCESS, ""));
        }

        stages.add(
                RuntimeAnswerPrompts.runtimeAnswerMetaStage(
                        policy,
                        effectivePromptContext.length(),
                        curated.finalCandidates().size(),
                        abstention,
                        abstentionReason));

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
                                RuntimeRetrievedSourceMapper.toChatSources(curated.finalCandidates())));
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
