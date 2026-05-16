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
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.uniovi.rag.configuration.RagRuntimeProperties;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FullCorpusWorkflowTest {

    @Test
    void execute_assemblesCorpus_andInvokesChat_andReturnsPlaceholderTraceResult() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("answer");
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content()).thenReturn("answer");

        SnapshotCorpusAssembler assembler = mock(SnapshotCorpusAssembler.class);
        when(assembler.assembleFullCorpusText(any(ExecutionContext.class)))
                .thenReturn("CORPUS");

        FullCorpusWorkflow wf = new FullCorpusWorkflow(chatClient, assembler, new RuntimePromptBudgeter(new RagRuntimeProperties()), null);

        ExecutionContext ctx = minimalCtx();
        RagExecutionResult out = wf.execute(ctx);

        assertThat(out.answerText()).isEqualTo("answer");
        verify(assembler).assembleFullCorpusText(any(ExecutionContext.class));
    }

    private static ExecutionContext minimalCtx() {
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

        return new ExecutionContext(
                userId,
                projectId,
                convId,
                "userQ",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "SYS",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of(),
                Optional.empty(),
                Optional.of(minimalPlan()),
                Optional.empty(),
                "",
                "",
                Optional.empty(),
                null,
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

    private static QueryPlan minimalPlan() {
        return new QueryPlan(
                QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                "raw",
                "planIn",
                "norm",
                "rew",
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
    }
}

