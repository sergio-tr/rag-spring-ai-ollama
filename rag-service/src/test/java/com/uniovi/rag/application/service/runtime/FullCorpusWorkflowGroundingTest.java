package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.testsupport.config.TestConfigurablePromptResolver;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvoker;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvokerTestSupport;
import org.springframework.ai.chat.client.ChatClient;

import com.uniovi.rag.configuration.RagRuntimeProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FullCorpusWorkflowGroundingTest {

    @Test
    void documentaryQuestionWithoutContext_returnsControlledInsufficientMessage() {
        RagLlmChatInvoker invoker = RagLlmChatInvokerTestSupport.stubContent("LLM should not answer this");
        SnapshotCorpusAssembler assembler = mock(SnapshotCorpusAssembler.class);
        when(assembler.assembleFullCorpusText(any())).thenReturn("");
        FullCorpusWorkflow workflow =
                new FullCorpusWorkflow(invoker, assembler, new RuntimePromptBudgeter(new RagRuntimeProperties()), TestConfigurablePromptResolver.answerPromptResolver(), null);

        var out = workflow.execute(ctxWithQuery("¿Cuántas actas mencionan el ascensor?"));
        assertThat(out.answerText()).isEqualTo(RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES);
    }

    @Test
    void documentaryQuestionWithContext_allowsNormalLlmAnswer() {
        RagLlmChatInvoker invoker = RagLlmChatInvokerTestSupport.stubContent("Hay una acta que lo menciona.");
        SnapshotCorpusAssembler assembler = mock(SnapshotCorpusAssembler.class);
        when(assembler.assembleFullCorpusText(any())).thenReturn("ACTA 1: ... ascensor ...");
        FullCorpusWorkflow workflow =
                new FullCorpusWorkflow(invoker, assembler, new RuntimePromptBudgeter(new RagRuntimeProperties()), TestConfigurablePromptResolver.answerPromptResolver(), null);

        var out = workflow.execute(ctxWithQuery("¿Cuántas actas mencionan el ascensor?"));
        assertThat(out.answerText()).isEqualTo("Hay una acta que lo menciona.");
    }

    @Test
    void generalQuestionWithoutCorpus_skipsLlm_andReturnsInsufficientDocumentMessage() {
        RagLlmChatInvoker invoker = RagLlmChatInvokerTestSupport.stubContent("Buenos dias");
        SnapshotCorpusAssembler assembler = mock(SnapshotCorpusAssembler.class);
        when(assembler.assembleFullCorpusText(any())).thenReturn("");
        FullCorpusWorkflow workflow =
                new FullCorpusWorkflow(invoker, assembler, new RuntimePromptBudgeter(new RagRuntimeProperties()), TestConfigurablePromptResolver.answerPromptResolver(), null);

        var out = workflow.execute(ctxWithQuery("buenos dias"));
        assertThat(out.answerText())
                .isIn(
                        RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES,
                        RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_EN);
        assertThat(out.workflowStageTraces())
                .anyMatch(s -> s.stageName().equals("llm") && s.outcome() == ExecutionStageOutcome.SKIPPED);
    }

    private static ExecutionContext ctxWithQuery(String query) {
        QueryPlan qp = mock(QueryPlan.class);
        when(qp.rewrittenQueryText()).thenReturn(query);
        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.queryPlan()).thenReturn(Optional.of(qp));
        when(ctx.effectiveSystemPrompt()).thenReturn("");
        when(ctx.chatModelOverride()).thenReturn(Optional.empty());
        when(ctx.knowledgeSnapshotSelection()).thenReturn(KnowledgeSnapshotSelection.empty());
        RagConfig rag =
                new RagConfig(
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        10,
                        0.7,
                        "l",
                        "e",
                        "c",
                        "SIMPLE",
                        true,
                        RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                        RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                        MaterializationStrategy.CHUNK_LEVEL);
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
