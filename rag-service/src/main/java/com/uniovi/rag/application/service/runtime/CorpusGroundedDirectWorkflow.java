package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvoker;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Lab P0: direct LLM generation using assembled documentary corpus (snapshot-backed chunks), without retrieval,
 * hybrid fusion, ranker, tools, or function calling.
 */
@Component
public class CorpusGroundedDirectWorkflow extends FullCorpusWorkflow {

    public CorpusGroundedDirectWorkflow(
            RagLlmChatInvoker llmChatInvoker,
            SnapshotCorpusAssembler snapshotCorpusAssembler,
            RuntimePromptBudgeter promptBudgeter,
            @Autowired(required = false) ObservabilitySupport observability) {
        super(llmChatInvoker, snapshotCorpusAssembler, promptBudgeter, observability);
    }

    @Override
    public String workflowName() {
        return "CorpusGroundedDirectWorkflow";
    }
}
