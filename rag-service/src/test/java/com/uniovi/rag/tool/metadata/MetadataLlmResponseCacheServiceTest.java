package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.config.llm.TaskLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetadataLlmResponseCacheServiceTest {

    @Mock
    private LlmClientResolver llmClientResolver;

    @Mock
    private ResolvedLlmConfigResolver resolvedLlmConfigResolver;

    @Mock
    private TaskLlmConfigResolver taskLlmConfigResolver;

    @Mock
    private LlmChatClient openAiChatClient;

    @Mock
    private LlmChatClient ollamaChatClient;

    private MetadataLlmResponseCacheService svc;

    @BeforeEach
    void setUp() {
        svc = new MetadataLlmResponseCacheService(llmClientResolver, resolvedLlmConfigResolver, taskLlmConfigResolver);
    }

    @AfterEach
    void tearDown() {
        OrchestrationLlmConfigScope.clear();
    }

    @Test
    void getCachedResponse_returnsEmpty_whenPromptBlank() {
        assertThat(svc.getCachedResponse(null)).isEqualTo("");
        assertThat(svc.getCachedResponse("   ")).isEqualTo("");
        verifyNoInteractions(llmClientResolver, resolvedLlmConfigResolver);
    }

    @Test
    void getCachedResponse_stripsNonEmptyResponse() {
        ResolvedLlmConfig config = openAiConfig();
        stubMetadataSecondaryCall(config, false);
        when(llmClientResolver.resolveChatClient(config)).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any(LlmChatRequest.class))).thenReturn(LlmChatResponse.ofContent("  OK  "));

        assertThat(svc.getCachedResponse("p")).isEqualTo("OK");
    }

    @Test
    void getCachedResponse_returnsEmpty_onIllegalArgumentException() {
        ResolvedLlmConfig config = openAiConfig();
        stubMetadataSecondaryCall(config, false);
        when(llmClientResolver.resolveChatClient(config)).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any(LlmChatRequest.class))).thenThrow(new IllegalArgumentException("bad"));

        assertThat(svc.getCachedResponse("p")).isEqualTo("");
    }

    @Test
    void metadataCacheUsesOpenAiCompatibleClientWhenRuntimeProviderIsOpenAiCompatible() {
        ResolvedLlmConfig config = openAiConfig();
        stubMetadataSecondaryCall(config, false);
        when(llmClientResolver.resolveChatClient(config)).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any(LlmChatRequest.class))).thenReturn(LlmChatResponse.ofContent("summary"));
        when(openAiChatClient.provider()).thenReturn(LlmProvider.OPENAI_COMPATIBLE);

        String out = svc.getCachedResponse("analyze these minutes");

        assertThat(out).isEqualTo("summary");
        verify(llmClientResolver).resolveChatClient(same(config));
        verify(openAiChatClient).chat(any(LlmChatRequest.class));
        assertThat(openAiChatClient.provider()).isEqualTo(LlmProvider.OPENAI_COMPATIBLE);

        ArgumentCaptor<LlmChatRequest> captor = ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(openAiChatClient).chat(captor.capture());
        assertThat(captor.getValue().model()).isEqualTo("gpt-oss:20b");
        assertThat(captor.getValue().temperature()).isEqualTo(0.1);
        assertThat(captor.getValue().timeoutMs()).isEqualTo(60_000);
    }

    @Test
    void metadataCacheDoesNotCallOllamaWhenProviderIsOpenAiCompatible() {
        ResolvedLlmConfig config = openAiConfig();
        stubMetadataSecondaryCall(config, false);
        when(llmClientResolver.resolveChatClient(config)).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any(LlmChatRequest.class))).thenReturn(LlmChatResponse.ofContent("ok"));

        svc.getCachedResponse("p");

        verify(llmClientResolver).resolveChatClient(config);
        verify(openAiChatClient).chat(any(LlmChatRequest.class));
        verify(ollamaChatClient, never()).chat(any());
    }

    @Test
    void metadataCacheUsesOllamaClientOnlyWhenProviderIsOllamaNative() {
        ResolvedLlmConfig config = ollamaConfig();
        stubMetadataSecondaryCall(config, false);
        when(llmClientResolver.resolveChatClient(config)).thenReturn(ollamaChatClient);
        when(ollamaChatClient.chat(any(LlmChatRequest.class))).thenReturn(LlmChatResponse.ofContent("ollama-ok"));

        String out = svc.getCachedResponse("count attendees");

        assertThat(out).isEqualTo("ollama-ok");
        verify(llmClientResolver).resolveChatClient(same(config));
        verify(ollamaChatClient).chat(any(LlmChatRequest.class));
        verify(resolvedLlmConfigResolver, never()).resolve(any(), any(), any());
    }

    @Test
    void metadataCacheUsesTaskOverrideModelWhenConfigured() {
        ResolvedLlmConfig overridden =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "task-override-model",
                        "qwen3-embedding:8b",
                        "OPENAI_COMPATIBLE_API_KEY",
                        null,
                        0.0,
                        60_000,
                        null,
                        Map.of());
        stubMetadataSecondaryCall("metadata-filter", overridden, true);
        when(llmClientResolver.resolveChatClient(overridden)).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any(LlmChatRequest.class))).thenReturn(LlmChatResponse.ofContent("ok"));

        svc.getCachedResponse("metadata-filter", "prompt");

        ArgumentCaptor<LlmChatRequest> captor = ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(openAiChatClient).chat(captor.capture());
        assertThat(captor.getValue().model()).isEqualTo("task-override-model");
        assertThat(captor.getValue().temperature()).isEqualTo(0.0);
    }

    @Test
    void getCachedResponse_withOperation_delegatesToCachedPath() {
        ResolvedLlmConfig config = openAiConfig();
        stubMetadataSecondaryCall("metadata-filter-and-list", config, false);
        when(llmClientResolver.resolveChatClient(config)).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any(LlmChatRequest.class))).thenReturn(LlmChatResponse.ofContent("OK"));

        assertThat(svc.getCachedResponse("metadata-filter-and-list", "prompt")).isEqualTo("OK");
        verify(openAiChatClient).chat(any(LlmChatRequest.class));
    }

    @Test
    void metadataYesNoFilterUsesOpenAiCompatibleClientWhenRuntimeProviderIsOpenAiCompatible() {
        ResolvedLlmConfig config = openAiConfig();
        stubMetadataSecondaryCall("metadata-yes-no-filter", config, false);
        when(llmClientResolver.resolveChatClient(config)).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any(LlmChatRequest.class))).thenReturn(LlmChatResponse.ofContent("YES"));

        String out = svc.getCachedResponse("metadata-yes-no-filter", "interpret yes/no");

        assertThat(out).isEqualTo("YES");
        verify(llmClientResolver).resolveChatClient(same(config));
        verify(openAiChatClient).chat(any(LlmChatRequest.class));
        verify(ollamaChatClient, never()).chat(any());
    }

    @Test
    void metadataTopicFilterMapsToMetadataReasoningTask() {
        ResolvedLlmConfig config = openAiConfig();
        stubMetadataSecondaryCall("metadata-topic-filter", config, false);
        when(llmClientResolver.resolveChatClient(config)).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any(LlmChatRequest.class))).thenReturn(LlmChatResponse.ofContent("NO"));

        assertThat(svc.getCachedResponse("metadata-topic-filter", "topic filter prompt")).isEqualTo("NO");

        verify(taskLlmConfigResolver, times(2))
                .resolveSecondaryCall(isNull(), isNull(), eq("metadata-topic-filter"), isNull(), isNull());
    }

    @Test
    void metadataCacheFailureDoesNotFallbackToOtherProvider() {
        ResolvedLlmConfig config = openAiConfig();
        stubMetadataSecondaryCall(config, false);
        when(llmClientResolver.resolveChatClient(config)).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any(LlmChatRequest.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertThat(svc.getCachedResponse("p")).isEqualTo("");

        verify(llmClientResolver, times(3)).resolveChatClient(config);
        verify(openAiChatClient, times(3)).chat(any(LlmChatRequest.class));
        verify(ollamaChatClient, never()).chat(any());
    }

    private void stubMetadataSecondaryCall(ResolvedLlmConfig config, boolean taskOverrideApplied) {
        stubMetadataSecondaryCall("metadata-reasoning", config, taskOverrideApplied);
    }

    private void stubMetadataSecondaryCall(String operation, ResolvedLlmConfig config, boolean taskOverrideApplied) {
        when(taskLlmConfigResolver.resolveSecondaryCall(isNull(), isNull(), eq(operation), isNull(), isNull()))
                .thenReturn(
                        new TaskLlmConfigResolver.SecondaryCallConfig(
                                config, config.chatModel(), config.temperature(), taskOverrideApplied));
    }

    private static ResolvedLlmConfig openAiConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                "gpt-oss:20b",
                "qwen3-embedding:8b",
                "OPENAI_COMPATIBLE_API_KEY",
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }

    private static ResolvedLlmConfig ollamaConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OLLAMA_NATIVE,
                "http://localhost:11434",
                "gemma3:4b",
                "mxbai-embed-large:latest",
                null,
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }
}
