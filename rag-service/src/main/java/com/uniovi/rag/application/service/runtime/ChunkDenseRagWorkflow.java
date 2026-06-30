package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvoker;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChunkDenseRagWorkflow extends AbstractDenseRagWorkflow {

    public ChunkDenseRagWorkflow(
            RagLlmChatInvoker llmChatInvoker,
            AdvancedRetrievalPipeline advancedRetrievalPipeline,
            @Autowired(required = false) ObservabilitySupport observability) {
        super(llmChatInvoker, advancedRetrievalPipeline, observability);
    }

    @Override
    public String workflowName() {
        return "ChunkDenseRagWorkflow";
    }
}
