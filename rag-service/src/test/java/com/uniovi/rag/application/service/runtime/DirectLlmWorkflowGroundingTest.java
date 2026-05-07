package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DirectLlmWorkflowGroundingTest {

    @Test
    void documentBoundQuestion_whenRetrievalRequired_returnsControlledMessage_andSkipsLlm() {
        ChatClient client = ChatClientTestSupport.clientWithUserPromptReturning("LLM should not answer this");
        DirectLlmWorkflow workflow = new DirectLlmWorkflow(client, null);

        var out =
                workflow.execute(ctxWithQuery("¿Quién presidió las actas donde se menciona el ascensor?", true));
        assertThat(out.answerText()).isEqualTo(RuntimeAnswerPrompts.DOCUMENT_BOUND_REQUIRES_RETRIEVAL_MESSAGE_ES);
        assertThat(out.workflowStageTraces())
                .anyMatch(s -> s.stageName().equals("llm") && s.outcome() == ExecutionStageOutcome.SKIPPED);
    }

    @Test
    void generalQuestion_allowsNormalLlmAnswer() {
        ChatClient client = ChatClientTestSupport.clientWithUserPromptReturning("Buenos días");
        DirectLlmWorkflow workflow = new DirectLlmWorkflow(client, null);

        var out = workflow.execute(ctxWithQuery("buenos dias", true));
        assertThat(out.answerText()).isEqualTo("Buenos días");
    }

    @Test
    void documentBoundQuestion_whenRetrievalDisabled_usesDirectBaselineLlm() {
        ChatClient client = ChatClientTestSupport.clientWithUserPromptReturning("baseline ok");
        DirectLlmWorkflow workflow = new DirectLlmWorkflow(client, null);

        var out =
                workflow.execute(ctxWithQuery("¿Quién presidió las actas donde se menciona el ascensor?", false));
        assertThat(out.answerText()).isEqualTo("baseline ok");
        assertThat(out.workflowStageTraces())
                .anyMatch(s -> s.stageName().equals("llm") && s.outcome() == ExecutionStageOutcome.SUCCESS);
    }

    private static ExecutionContext ctxWithQuery(String query, boolean useRetrieval) {
        QueryPlan qp = mock(QueryPlan.class);
        when(qp.rewrittenQueryText()).thenReturn(query);
        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.queryPlan()).thenReturn(Optional.of(qp));
        when(ctx.effectiveSystemPrompt()).thenReturn("");
        when(ctx.chatModelOverride()).thenReturn(Optional.empty());

        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        fc.setUseRetrieval(useRetrieval);
        RagConfig rag = RagConfig.fromFeatureConfiguration(fc, 10, 0.7, "l", "e", "c", "SIMPLE");
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        SystemPromptLayers.empty(),
                        "",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        null);
        when(ctx.resolved()).thenReturn(resolved);
        when(ctx.userQuery()).thenReturn(query);
        when(ctx.conversationId()).thenReturn(UUID.randomUUID());
        return ctx;
    }
}
