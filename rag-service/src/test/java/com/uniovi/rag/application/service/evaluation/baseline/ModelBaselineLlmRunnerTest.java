package com.uniovi.rag.application.service.evaluation.baseline;

import com.uniovi.rag.domain.evaluation.BenchmarkEvaluationProtocol;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelBaselineLlmRunnerTest {

    @Test
    void generateAnswer_usesSystemUserOptionsChain_nonStreaming() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content())
                .thenReturn("oracle-out");

        ModelBaselineLlmRunner runner = new ModelBaselineLlmRunner(chatClient);
        LlmExperimentalSnapshot snap =
                new LlmExperimentalSnapshot(
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
                        null,
                        null,
                        null,
                        Map.of(),
                        Map.of(),
                        List.of());
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
    }

    @Test
    void generateAnswerFromRetrievedChunks_buildsUserTurn() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content())
                .thenReturn("rag-out");

        ModelBaselineLlmRunner runner = new ModelBaselineLlmRunner(chatClient);
        LlmExperimentalSnapshot snap =
                new LlmExperimentalSnapshot(
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
                        null,
                        null,
                        null,
                        Map.of(),
                        Map.of(),
                        List.of());
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
    }
}
