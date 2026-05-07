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
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FullCorpusWorkflowGroundingTest {

    @Test
    void documentaryQuestionWithoutContext_returnsControlledInsufficientMessage() {
        ChatClient client = ChatClientTestSupport.clientWithUserPromptReturning("LLM should not answer this");
        SnapshotCorpusAssembler assembler = mock(SnapshotCorpusAssembler.class);
        when(assembler.assembleFullCorpusText(any())).thenReturn("");
        FullCorpusWorkflow workflow = new FullCorpusWorkflow(client, assembler, null);

        var out = workflow.execute(ctxWithQuery("¿Cuántas actas mencionan el ascensor?"));
        assertThat(out.answerText()).isEqualTo(RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES);
    }

    @Test
    void documentaryQuestionWithContext_allowsNormalLlmAnswer() {
        ChatClient client = ChatClientTestSupport.clientWithUserPromptReturning("Hay una acta que lo menciona.");
        SnapshotCorpusAssembler assembler = mock(SnapshotCorpusAssembler.class);
        when(assembler.assembleFullCorpusText(any())).thenReturn("ACTA 1: ... ascensor ...");
        FullCorpusWorkflow workflow = new FullCorpusWorkflow(client, assembler, null);

        var out = workflow.execute(ctxWithQuery("¿Cuántas actas mencionan el ascensor?"));
        assertThat(out.answerText()).isEqualTo("Hay una acta que lo menciona.");
    }

    @Test
    void generalQuestionWithoutContext_canUseGeneralLlmAnswer() {
        ChatClient client = ChatClientTestSupport.clientWithUserPromptReturning("Buenos dias");
        SnapshotCorpusAssembler assembler = mock(SnapshotCorpusAssembler.class);
        when(assembler.assembleFullCorpusText(any())).thenReturn("");
        FullCorpusWorkflow workflow = new FullCorpusWorkflow(client, assembler, null);

        var out = workflow.execute(ctxWithQuery("buenos dias"));
        assertThat(out.answerText()).isEqualTo("Buenos dias");
    }

    private static ExecutionContext ctxWithQuery(String query) {
        QueryPlan qp = mock(QueryPlan.class);
        when(qp.rewrittenQueryText()).thenReturn(query);
        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.queryPlan()).thenReturn(Optional.of(qp));
        when(ctx.effectiveSystemPrompt()).thenReturn("");
        when(ctx.chatModelOverride()).thenReturn(Optional.empty());
        when(ctx.knowledgeSnapshotSelection()).thenReturn(KnowledgeSnapshotSelection.empty());
        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        fc.setUseRetrieval(true);
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
