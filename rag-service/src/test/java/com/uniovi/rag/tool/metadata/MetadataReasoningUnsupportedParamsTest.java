package com.uniovi.rag.tool.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.config.llm.TaskLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.TaskLlmRoleDefaults;
import com.uniovi.rag.domain.llm.TaskLlmTask;
import com.uniovi.rag.infrastructure.llm.openaicompat.OpenAiCompatibleLlmException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetadataReasoningUnsupportedParamsTest {

    @Mock
    private LlmClientResolver llmClientResolver;

    @Mock
    private ResolvedLlmConfigResolver resolvedLlmConfigResolver;

    @Mock
    private TaskLlmConfigResolver taskLlmConfigResolver;

    @Mock
    private LlmChatClient chatClient;

    private MetadataLlmResponseCacheService service;

    @BeforeEach
    void setUp() {
        service = new MetadataLlmResponseCacheService(llmClientResolver, resolvedLlmConfigResolver, taskLlmConfigResolver);
    }

    @Test
    void metadataReasoning_resolvesTaskDefaultsWithPenaltiesButClientFiltersBeforeSend() {
        ResolvedLlmConfig merged =
                new ResolvedLlmConfig(
                        LlmProvider.OPENAI_COMPATIBLE,
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "qwen3.5:9b",
                        "qwen3-embedding:8b",
                        "OPENAI_COMPATIBLE_API_KEY",
                        null,
                        TaskLlmRoleDefaults.forTask(TaskLlmTask.METADATA_REASONING).parameters().temperature(),
                        60_000,
                        null,
                        TaskLlmRoleDefaults.forTask(TaskLlmTask.METADATA_REASONING).parameters().toAdditionalParameters());

        when(taskLlmConfigResolver.resolveSecondaryCall(isNull(), isNull(), eq("metadata-reasoning"), isNull(), isNull()))
                .thenReturn(
                        new TaskLlmConfigResolver.SecondaryCallConfig(
                                merged, "qwen3.5:9b", merged.temperature(), true));
        when(llmClientResolver.resolveChatClient(merged)).thenReturn(chatClient);
        when(chatClient.chat(any(LlmChatRequest.class))).thenReturn(LlmChatResponse.ofContent("summary"));

        String out = service.getCachedResponse("metadata-reasoning", "analyze minutes");

        assertThat(out).isEqualTo("summary");
        ArgumentCaptor<LlmChatRequest> captor = ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(chatClient).chat(captor.capture());
        assertThat(captor.getValue().additionalParameters())
                .containsEntry("presencePenalty", 0.0)
                .containsEntry("frequencyPenalty", 0.0);
    }

    @Test
    void metadataReasoning_doesNotRetryUnsupportedParamsError() {
        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "qwen3.5:9b",
                        "qwen3-embedding:8b",
                        "OPENAI_COMPATIBLE_API_KEY",
                        null,
                        0.0,
                        60_000,
                        null,
                        Map.of("presencePenalty", 0.0));
        when(taskLlmConfigResolver.resolveSecondaryCall(isNull(), isNull(), eq("metadata-reasoning"), isNull(), isNull()))
                .thenReturn(new TaskLlmConfigResolver.SecondaryCallConfig(config, "qwen3.5:9b", 0.0, true));
        when(llmClientResolver.resolveChatClient(config)).thenReturn(chatClient);
        when(chatClient.chat(any(LlmChatRequest.class)))
                .thenThrow(
                        OpenAiCompatibleLlmException.unsupportedParams(
                                "litellm.UnsupportedParamsError: ollama does not support parameters: ['presence_penalty']"));

        assertThat(service.getCachedResponse("metadata-reasoning", "prompt")).isEmpty();

        verify(chatClient, times(1)).chat(any(LlmChatRequest.class));
    }
}
