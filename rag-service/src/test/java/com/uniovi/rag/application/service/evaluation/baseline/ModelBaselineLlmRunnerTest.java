package com.uniovi.rag.application.service.evaluation.baseline;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.evaluation.BenchmarkEvaluationProtocol;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import com.uniovi.rag.application.service.llm.LlmClientResolver;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelBaselineLlmRunnerTest {

    @Mock private LlmClientResolver llmClientResolver;
    @Mock private ResolvedLlmConfigResolver configResolver;
    @Mock private EvaluationRunRepository evaluationRunRepository;
    @Mock private LlmChatClient llmChatClient;

    @Test
    void generateAnswer_usesProviderAwareClient_nonStreaming() {
        when(configResolver.resolve(any(), any(), any())).thenReturn(openAiBaseConfig());
        when(llmClientResolver.resolveChatClient(any())).thenReturn(llmChatClient);
        when(llmChatClient.chat(any())).thenReturn(new LlmChatResponse("oracle-out", "m", null, null, Map.of()));

        ModelBaselineLlmRunner runner =
                new ModelBaselineLlmRunner(llmClientResolver, configResolver, evaluationRunRepository);
        LlmExperimentalSnapshot snap = openAiSnapshot();
        PromptProfileSnapshot prompts =
                new PromptProfileSnapshot("pv", "b", "p", "", "", "", "SYS", "h");

        String out =
                runner.generateAnswer(
                        BenchmarkEvaluationProtocol.LLM_READER_ORACLE_CONTEXT,
                        snap,
                        prompts,
                        "What?",
                        "CTX body",
                        "",
                        null);
        assertThat(out).isEqualTo("oracle-out");

        ArgumentCaptor<ResolvedLlmConfig> configCaptor = ArgumentCaptor.forClass(ResolvedLlmConfig.class);
        verify(llmClientResolver).resolveChatClient(configCaptor.capture());
        assertThat(configCaptor.getValue().chatProvider()).isEqualTo(LlmProvider.OPENAI_COMPATIBLE);
    }

    @Test
    void generateAnswerFromRetrievedChunks_buildsUserTurn() {
        when(configResolver.resolve(any(), any(), any())).thenReturn(openAiBaseConfig());
        when(llmClientResolver.resolveChatClient(any())).thenReturn(llmChatClient);
        when(llmChatClient.chat(any())).thenReturn(new LlmChatResponse("rag-out", "m", null, null, Map.of()));

        ModelBaselineLlmRunner runner =
                new ModelBaselineLlmRunner(llmClientResolver, configResolver, evaluationRunRepository);
        LlmExperimentalSnapshot snap = openAiSnapshot();
        PromptProfileSnapshot prompts =
                new PromptProfileSnapshot(
                        "pv",
                        "",
                        "",
                        "",
                        EvaluationBaselinePrompts.RETRIEVAL_QUESTION_TEMPLATE,
                        EvaluationBaselinePrompts.ANSWER_FORMATTING,
                        "EFF",
                        "hh");

        String ans =
                runner.generateAnswerFromRetrievedChunks(
                        snap,
                        prompts,
                        "Q",
                        List.of(new Document("id1", "chunk text", Map.of())));
        assertThat(ans).isEqualTo("rag-out");

        ArgumentCaptor<LlmChatRequest> requestCaptor = ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(llmChatClient).chat(requestCaptor.capture());
        assertThat(requestCaptor.getValue().model()).isEqualTo("m");
    }

    private static ResolvedLlmConfig openAiBaseConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://gateway.test/v1",
                "default-chat",
                "default-emb",
                "OPENAI_COMPATIBLE_API_KEY",
                null,
                0.0,
                60000,
                "",
                Map.of());
    }

    private static LlmExperimentalSnapshot openAiSnapshot() {
        return new LlmExperimentalSnapshot(
                "m",
                0.2,
                0.9,
                10,
                null,
                1.05,
                2048,
                128,
                null,
                1,
                List.of(),
                null,
                false,
                LlmProvider.OPENAI_COMPATIBLE.name(),
                LlmProvider.OPENAI_COMPATIBLE.name(),
                60000,
                Map.of(),
                Map.of(),
                List.of());
    }
}
