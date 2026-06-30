package com.uniovi.rag.application.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.config.llm.TaskLlmConfigResolver;
import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderAwareSecondaryLlmExecutorTest {

    @Mock private LlmClientResolver llmClientResolver;
    @Mock private ResolvedLlmConfigResolver resolvedLlmConfigResolver;
    @Mock private TaskLlmConfigResolver taskLlmConfigResolver;
    @Mock private ChatGenerationModelSelector chatGenerationModelSelector;
    @Mock private LlmChatClient chatClient;

    private ProviderAwareSecondaryLlmExecutor executor;

    @BeforeEach
    void setUp() {
        executor =
                new ProviderAwareSecondaryLlmExecutor(
                        llmClientResolver,
                        resolvedLlmConfigResolver,
                        taskLlmConfigResolver,
                        chatGenerationModelSelector,
                        new ObjectMapper());
    }

    @Test
    void complete_usesTaskOverrideModelAndTemperature() {
        ResolvedLlmConfig config = baseConfig();
        TaskLlmConfigResolver.SecondaryCallConfig call =
                new TaskLlmConfigResolver.SecondaryCallConfig(config, "task-model", 0.0, true);
        when(taskLlmConfigResolver.resolveSecondaryCall(isNull(), isNull(), eq("query-rewrite"), isNull(), isNull()))
                .thenReturn(call);
        when(llmClientResolver.resolveChatClient(config)).thenReturn(chatClient);
        when(chatClient.chat(any(LlmChatRequest.class))).thenReturn(LlmChatResponse.ofContent("ok"));

        String out = executor.complete("query-rewrite", null, "user");

        assertThat(out).isEqualTo("ok");
        ArgumentCaptor<LlmChatRequest> captor = ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(chatClient).chat(captor.capture());
        assertThat(captor.getValue().model()).isEqualTo("task-model");
        assertThat(captor.getValue().temperature()).isEqualTo(0.0);
    }

    @Test
    void completeWithContext_prefersTaskModelOverSelector() {
        ExecutionContext ctx = mock(ExecutionContext.class);

        ResolvedLlmConfig config = baseConfig();
        TaskLlmConfigResolver.SecondaryCallConfig call =
                new TaskLlmConfigResolver.SecondaryCallConfig(config, "judge-model", 0.0, true);
        when(taskLlmConfigResolver.resolveSecondaryCall(eq(ctx), eq("runtime-judge"), isNull(), eq("selector-model")))
                .thenReturn(call);
        when(chatGenerationModelSelector.effectiveChatModelId(ctx)).thenReturn(Optional.of("selector-model"));
        when(llmClientResolver.resolveChatClient(config)).thenReturn(chatClient);
        when(chatClient.chat(any(LlmChatRequest.class))).thenReturn(LlmChatResponse.ofContent("ACCEPTED"));

        String out = executor.complete(ctx, "runtime-judge", null, "prompt", null);

        assertThat(out).isEqualTo("ACCEPTED");
        ArgumentCaptor<LlmChatRequest> captor = ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(chatClient).chat(captor.capture());
        assertThat(captor.getValue().model()).isEqualTo("judge-model");
    }

    private static ResolvedLlmConfig baseConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                "base-chat",
                "embed",
                "OPENAI_COMPATIBLE_API_KEY",
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }
}
