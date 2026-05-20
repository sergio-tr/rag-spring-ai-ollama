package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.retrieval.CompressionOutcome;
import com.uniovi.rag.domain.runtime.retrieval.CuratedContextSet;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChunkDenseMetadataWorkflowTest {

    @Test
    void execute_whenAdvisorPackedContextPresent_skipsAdvancedRetrieval() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("answer");
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content()).thenReturn("answer");

        AdvancedRetrievalPipeline pipeline = mock(AdvancedRetrievalPipeline.class);
        ChunkDenseMetadataWorkflow wf = new ChunkDenseMetadataWorkflow(chatClient, pipeline, null);

        ExecutionContext ctx = minimalCtx(Optional.of(new PackedContextSet(List.of(), "s", 0, 0, List.of(), "CTX")));
        RagExecutionResult out = wf.execute(ctx);

        assertThat(out.answerText()).isEqualTo("answer");
        verify(pipeline, never()).retrieve(any(), any(), anyString());
    }

    @Test
    void execute_whenNoAdvisorPackedContext_usesAdvancedRetrievalPromptContext() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("answer2");
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content()).thenReturn("answer2");

        AdvancedRetrievalPipeline pipeline = mock(AdvancedRetrievalPipeline.class);
        when(pipeline.retrieve(any(ExecutionContext.class), any(QueryPlan.class), eq("ChunkDenseMetadataWorkflow")))
                .thenReturn(
                        new CuratedContextSet(
                                List.of(dummyCandidate()),
                                "PROMPT_CTX",
                                new CompressionOutcome(1, 1, 0, List.of()),
                                List.of(),
                                new RetrievalDiagnostics(
                                        RetrievalMode.DENSE_ONLY,
                                        Optional.empty(),
                                        "",
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        false,
                                        List.of(),
                                        List.of(),
                                        Optional.empty()),
                                List.of(),
                                List.of(new ExecutionStageTrace("retrieval", 1, null, ""))));

        ChunkDenseMetadataWorkflow wf = new ChunkDenseMetadataWorkflow(chatClient, pipeline, null);

        ExecutionContext ctx = minimalCtx(Optional.empty());
        RagExecutionResult out = wf.execute(ctx);

        assertThat(out.answerText()).isEqualTo("answer2");
        verify(pipeline).retrieve(any(ExecutionContext.class), any(QueryPlan.class), eq("ChunkDenseMetadataWorkflow"));
    }

    @Test
    void execute_whenDocBoundEmptyContext_returnsExactAbstention() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("should_not_be_used");
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content()).thenReturn("should_not_be_used");

        AdvancedRetrievalPipeline pipeline = mock(AdvancedRetrievalPipeline.class);
        when(pipeline.retrieve(any(ExecutionContext.class), any(QueryPlan.class), eq("ChunkDenseMetadataWorkflow")))
                .thenReturn(
                        new CuratedContextSet(
                                List.of(),
                                "",
                                new CompressionOutcome(0, 0, 0, List.of()),
                                List.of(),
                                new RetrievalDiagnostics(
                                        RetrievalMode.DENSE_ONLY,
                                        Optional.empty(),
                                        "",
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        false,
                                        List.of(),
                                        List.of(),
                                        Optional.empty()),
                                List.of(),
                                List.of(new ExecutionStageTrace("retrieval", 1, null, ""))));

        ChunkDenseMetadataWorkflow wf = new ChunkDenseMetadataWorkflow(chatClient, pipeline, null);

        ExecutionContext ctx = minimalCtx(Optional.empty(), "hazme un resumen del acta del 25 de febrero de 2025");
        RagExecutionResult out = wf.execute(ctx);

        assertThat(out.answerText()).isEqualTo(RuntimeAnswerPrompts.INSUFFICIENT_DOCUMENT_CONTEXT_MESSAGE_ES);
    }

    @Test
    void execute_question25Feb2026_withOnly2025Source_abstainsWithoutCallingLlm() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        clearInvocations(chatClient);

        AdvancedRetrievalPipeline pipeline = mock(AdvancedRetrievalPipeline.class);
        when(pipeline.retrieve(any(ExecutionContext.class), any(QueryPlan.class), eq("ChunkDenseMetadataWorkflow")))
                .thenReturn(
                        new CuratedContextSet(
                                List.of(
                                        dummyCandidateWithFilename(
                                                "ACTA2.pdf", "Fecha: 25 de febrero de 2025. Presidente: Carlos.")),
                                "ACTA2.pdf — Fecha: 25 de febrero de 2025",
                                new CompressionOutcome(1, 1, 0, List.of()),
                                List.of(),
                                new RetrievalDiagnostics(
                                        RetrievalMode.DENSE_ONLY,
                                        Optional.empty(),
                                        "",
                                        1,
                                        0,
                                        1,
                                        1,
                                        1,
                                        1,
                                        1,
                                        0,
                                        0,
                                        false,
                                        List.of(),
                                        List.of(),
                                        Optional.empty()),
                                List.of(),
                                List.of(new ExecutionStageTrace("retrieval", 1, null, ""))));

        ChunkDenseMetadataWorkflow wf = new ChunkDenseMetadataWorkflow(chatClient, pipeline, null);
        ExecutionContext ctx =
                minimalCtx(Optional.empty(), "¿Quién fue el presidente del acta del 25/02/2026?");
        RagExecutionResult out = wf.execute(ctx);

        assertThat(out.answerText())
                .contains("2026-02-25")
                .doesNotContain("Presidente: Carlos");
        assertThat(out.answerText()).contains("2025-02-25");
        assertThat(out.workflowStageTraces())
                .anyMatch(s -> "date_grounding_answer_policy".equals(s.stageName())
                        && s.message().contains("dateMismatchDetected=true"));
        verify(chatClient, never()).prompt();
    }

    @Test
    void execute_whenDocBoundDateMismatch_abstainsWithoutCallingLlm() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("llm_summary");
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content()).thenReturn("llm_summary");
        clearInvocations(chatClient);

        AdvancedRetrievalPipeline pipeline = mock(AdvancedRetrievalPipeline.class);
        when(pipeline.retrieve(any(ExecutionContext.class), any(QueryPlan.class), eq("ChunkDenseMetadataWorkflow")))
                .thenReturn(
                        new CuratedContextSet(
                                List.of(
                                        dummyCandidateWithFilename("ACTA 5.pdf", "Fecha: 25 de febrero de 2026"),
                                        dummyCandidateWithFilename("ACTA 3.pdf", "Fecha: 25/08/2025")),
                                "ACTA 5.pdf — Fecha: 25 de febrero de 2026\nACTA 3.pdf — Fecha: 25/08/2025",
                                new CompressionOutcome(1, 1, 0, List.of()),
                                List.of(),
                                new RetrievalDiagnostics(
                                        RetrievalMode.DENSE_ONLY,
                                        Optional.empty(),
                                        "",
                                        2,
                                        0,
                                        2,
                                        2,
                                        2,
                                        2,
                                        2,
                                        0,
                                        0,
                                        false,
                                        List.of(),
                                        List.of(),
                                        Optional.empty()),
                                List.of(),
                                List.of(new ExecutionStageTrace("retrieval", 1, null, ""))));

        ChunkDenseMetadataWorkflow wf = new ChunkDenseMetadataWorkflow(chatClient, pipeline, null);

        ExecutionContext ctx = minimalCtx(Optional.empty(), "hazme un resumen del acta del 25 de febrero de 2025");
        RagExecutionResult out = wf.execute(ctx);

        assertThat(out.answerText())
                .contains("No he encontrado un acta con fecha 2025-02-25")
                .contains("ACTA 5.pdf (2026-02-25)");
        assertThat(out.workflowStageTraces())
                .anyMatch(s -> "date_grounding_answer_policy".equals(s.stageName())
                        && s.message().contains("dateMismatchDetected=true"));
        assertThat(out.workflowStageTraces())
                .anyMatch(s -> "runtime_answer_meta".equals(s.stageName())
                        && s.message().contains("abstention=true")
                        && s.message().contains("no_exact_date_source"));
        verify(chatClient, never()).prompt();
    }

    @Test
    void execute_whenPromptEmptyButCandidatesPresent_callsLlmUsingFallbackContext() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("from_fallback");
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content()).thenReturn("from_fallback");

        AdvancedRetrievalPipeline pipeline = mock(AdvancedRetrievalPipeline.class);
        when(pipeline.retrieve(any(ExecutionContext.class), any(QueryPlan.class), eq("ChunkDenseMetadataWorkflow")))
                .thenReturn(
                        new CuratedContextSet(
                                List.of(dummyCandidateWithFilename("ACTA 5.pdf", "Fecha: 25 de febrero de 2026")),
                                "",
                                new CompressionOutcome(1, 0, 1, List.of("all_dropped")),
                                List.of(),
                                new RetrievalDiagnostics(
                                        RetrievalMode.DENSE_ONLY,
                                        Optional.empty(),
                                        "",
                                        1,
                                        0,
                                        1,
                                        1,
                                        1,
                                        0,
                                        0,
                                        0,
                                        0,
                                        false,
                                        List.of(),
                                        List.of(),
                                        Optional.empty()),
                                List.of(),
                                List.of(new ExecutionStageTrace("retrieval", 1, null, ""))));

        ChunkDenseMetadataWorkflow wf = new ChunkDenseMetadataWorkflow(chatClient, pipeline, null);

        ExecutionContext ctx = minimalCtx(Optional.empty(), "hazme un resumen del acta del 25 de febrero de 2026");
        RagExecutionResult out = wf.execute(ctx);

        assertThat(out.answerText()).isEqualTo("from_fallback");
        verify(chatClient, atLeastOnce()).prompt();
    }

    @Test
    void execute_whenDocBoundExactDatePresent_callsLlm() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("summary_ok");
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content()).thenReturn("summary_ok");

        AdvancedRetrievalPipeline pipeline = mock(AdvancedRetrievalPipeline.class);
        when(pipeline.retrieve(any(ExecutionContext.class), any(QueryPlan.class), eq("ChunkDenseMetadataWorkflow")))
                .thenReturn(
                        new CuratedContextSet(
                                List.of(dummyCandidateWithFilename("ACTA 7.pdf", "Fecha: 25 de febrero de 2025\nContenido: ...")),
                                "ACTA 7.pdf — Fecha: 25 de febrero de 2025\nContenido: ...",
                                new CompressionOutcome(1, 1, 0, List.of()),
                                List.of(),
                                new RetrievalDiagnostics(
                                        RetrievalMode.DENSE_ONLY,
                                        Optional.empty(),
                                        "",
                                        1,
                                        0,
                                        1,
                                        1,
                                        1,
                                        1,
                                        1,
                                        0,
                                        0,
                                        false,
                                        List.of(),
                                        List.of(),
                                        Optional.empty()),
                                List.of(),
                                List.of(new ExecutionStageTrace("retrieval", 1, null, ""))));

        ChunkDenseMetadataWorkflow wf = new ChunkDenseMetadataWorkflow(chatClient, pipeline, null);

        ExecutionContext ctx = minimalCtx(Optional.empty(), "hazme un resumen del acta del 25 de febrero de 2025");
        RagExecutionResult out = wf.execute(ctx);

        assertThat(out.answerText()).isEqualTo("summary_ok");
        // Invoked at least once for the document-bound LLM path.
        verify(chatClient, atLeastOnce()).prompt();
    }

    private static RetrievalCandidate dummyCandidate() {
        return new RetrievalCandidate(
                "c1",
                "x",
                Map.of(),
                0,
                0,
                0,
                0,
                UUID.randomUUID(),
                0);
    }

    private static RetrievalCandidate dummyCandidateWithFilename(String filename, String content) {
        return new RetrievalCandidate(
                "c_" + filename,
                content,
                Map.of("filename", filename),
                0,
                0,
                0,
                0,
                UUID.randomUUID(),
                0);
    }

    private static ExecutionContext minimalCtx(Optional<PackedContextSet> packed) {
        return minimalCtx(packed, "userQ");
    }

    private static ExecutionContext minimalCtx(Optional<PackedContextSet> packed, String userQuery) {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();

        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        RagConfig cfg = RagConfig.fromFeatureConfiguration(fc, 10, 0.7, "llm", "emb", "clf", "SIMPLE");
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        cfg,
                        CapabilitySet.fromRagConfig(cfg),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        SystemPromptLayers.empty(),
                        "SYS",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        null);

        QueryPlan plan =
                new QueryPlan(
                        QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                        userQuery,
                        "planIn",
                        userQuery,
                        userQuery,
                        "label",
                        Optional.empty(),
                        ClassifierStatus.DISABLED,
                        QueryIntent.UNKNOWN,
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote("n"),
                        StructuredRewriteResult.identityDisabled("norm", null),
                        ExpectedAnswerShape.UNKNOWN,
                        AmbiguityAssessment.sufficient(),
                        "corr",
                        "m",
                        List.of());

        return new ExecutionContext(
                userId,
                projectId,
                convId,
                userQuery,
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "SYS",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of(),
                Optional.empty(),
                Optional.of(plan),
                packed,
                "",
                "",
                Optional.empty(),
                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                List.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty());
    }
}

