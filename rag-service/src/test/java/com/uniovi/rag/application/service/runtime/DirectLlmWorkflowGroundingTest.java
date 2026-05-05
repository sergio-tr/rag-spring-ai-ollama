package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DirectLlmWorkflowGroundingTest {

    @Test
    void documentBoundQuestion_returnsControlledMessage_andSkipsLlm() {
        ChatClient client = ChatClientTestSupport.clientWithUserPromptReturning("LLM should not answer this");
        DirectLlmWorkflow workflow = new DirectLlmWorkflow(client, null);

        var out = workflow.execute(ctxWithQuery("¿Quién presidió las actas donde se menciona el ascensor?"));
        assertThat(out.answerText()).isEqualTo(RuntimeAnswerPrompts.DOCUMENT_BOUND_REQUIRES_RETRIEVAL_MESSAGE_ES);
        assertThat(out.workflowStageTraces())
                .anyMatch(s -> s.stageName().equals("llm") && s.outcome() == ExecutionStageOutcome.SKIPPED);
    }

    @Test
    void generalQuestion_allowsNormalLlmAnswer() {
        ChatClient client = ChatClientTestSupport.clientWithUserPromptReturning("Buenos días");
        DirectLlmWorkflow workflow = new DirectLlmWorkflow(client, null);

        var out = workflow.execute(ctxWithQuery("buenos dias"));
        assertThat(out.answerText()).isEqualTo("Buenos días");
    }

    private static ExecutionContext ctxWithQuery(String query) {
        QueryPlan qp = mock(QueryPlan.class);
        when(qp.rewrittenQueryText()).thenReturn(query);
        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.queryPlan()).thenReturn(Optional.of(qp));
        when(ctx.effectiveSystemPrompt()).thenReturn("");
        when(ctx.chatModelOverride()).thenReturn(Optional.empty());
        return ctx;
    }
}

