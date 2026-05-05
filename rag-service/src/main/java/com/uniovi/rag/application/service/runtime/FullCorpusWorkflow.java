package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FullCorpusWorkflow extends AbstractExecutionWorkflow {

    private final SnapshotCorpusAssembler snapshotCorpusAssembler;

    public FullCorpusWorkflow(
            ChatClient chatClient,
            SnapshotCorpusAssembler snapshotCorpusAssembler,
            @Autowired(required = false) ObservabilitySupport observability) {
        super(chatClient, observability);
        this.snapshotCorpusAssembler = snapshotCorpusAssembler;
    }

    @Override
    public RagExecutionResult execute(ExecutionContext ctx) {
        long t0 = System.nanoTime();
        List<ExecutionStageTrace> stages = new ArrayList<>();
        String corpus = snapshotCorpusAssembler.assembleFullCorpusText(ctx);
        stages.add(stage("full_corpus_assembly", t0, ExecutionStageOutcome.SUCCESS, ""));
        long t1 = System.nanoTime();
        String q = canonicalGenerationQuery(ctx);
        String answer;
        boolean docBound = RuntimeAnswerPrompts.requiresStrictDocumentGrounding(q);
        if (docBound && (corpus == null || corpus.isBlank())) {
            answer = RuntimeAnswerPrompts.insufficientDocumentContextMessageFor(q);
            stages.add(stage("llm", t1, ExecutionStageOutcome.SKIPPED, "strict_document_grounding_no_context"));
        } else {
            String user = RuntimeAnswerPrompts.ragUserTurn(q, corpus, docBound);
            answer = invokeChat(ctx, ctx.effectiveSystemPrompt(), user);
            stages.add(stage("llm", t1, ExecutionStageOutcome.SUCCESS, ""));
        }
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
