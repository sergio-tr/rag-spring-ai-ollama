package com.uniovi.rag.application.service.runtime.query.analyser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.config.llm.TaskLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MinuteNERQueryAnalyserProviderAwareTest {

    @Mock private ResolvedLlmConfigResolver configResolver;
    @Mock private LlmClientResolver llmClientResolver;
    @Mock private TaskLlmConfigResolver taskLlmConfigResolver;
    @Mock private LlmChatClient openAiChatClient;

    private ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;
    private MinuteNERQueryAnalyser analyser;
    private LlmProperties properties;

    @BeforeEach
    void setUp() {
        properties = LlmModelCatalogTestSupport.openAiLiteLlmProperties();
        ResolvedLlmConfig config = openAiConfig(properties);
        when(taskLlmConfigResolver.resolveSecondaryCall(
                        ArgumentMatchers.isNull(),
                        ArgumentMatchers.isNull(),
                        ArgumentMatchers.eq("ner"),
                        ArgumentMatchers.isNull(),
                        ArgumentMatchers.isNull()))
                .thenReturn(new TaskLlmConfigResolver.SecondaryCallConfig(config, config.chatModel(), 0.0, false));
        secondaryLlmExecutor =
                new ProviderAwareSecondaryLlmExecutor(
                        llmClientResolver,
                        configResolver,
                        taskLlmConfigResolver,
                        mock(ChatGenerationModelSelector.class),
                        new ObjectMapper());
        when(llmClientResolver.resolveChatClient(any())).thenReturn(openAiChatClient);
        analyser = new MinuteNERQueryAnalyser(secondaryLlmExecutor);
    }

    @AfterEach
    void tearDown() {
        OrchestrationLlmConfigScope.clear();
    }

    @Test
    void minuteNerUsesOpenAiCompatibleClientWhenProviderIsOpenAiCompatible() {
        when(openAiChatClient.chat(any()))
                .thenReturn(LlmChatResponse.ofContent("{\"date\":[\"2025-01-15\"],\"answerType\":\"person\"}"));

        analyser.analyse("¿Quién presidió el 15 de enero de 2025?");

        ArgumentCaptor<ResolvedLlmConfig> configCaptor = ArgumentCaptor.forClass(ResolvedLlmConfig.class);
        verify(llmClientResolver).resolveChatClient(configCaptor.capture());
        assertThat(configCaptor.getValue().chatProvider()).isEqualTo(LlmProvider.OPENAI_COMPATIBLE);
        assertThat(configCaptor.getValue().chatModel())
                .isEqualTo(properties.getOpenAiCompatible().getDefaultChatModel());
    }

    @Test
    void minuteNerDoesNotUseDefaultOllamaChatClient() {
        when(openAiChatClient.chat(any()))
                .thenReturn(LlmChatResponse.ofContent("{\"date\":[],\"answerType\":\"text\"}"));

        analyser.analyse("query");

        ArgumentCaptor<ResolvedLlmConfig> configCaptor = ArgumentCaptor.forClass(ResolvedLlmConfig.class);
        verify(llmClientResolver).resolveChatClient(configCaptor.capture());
        assertThat(configCaptor.getValue().chatProvider()).isEqualTo(LlmProvider.OPENAI_COMPATIBLE);
        assertThat(configCaptor.getValue().chatProvider()).isNotEqualTo(LlmProvider.OLLAMA_NATIVE);
    }

    private static ResolvedLlmConfig openAiConfig(LlmProperties properties) {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                properties.getOpenAiCompatible().getDefaultBaseUrl(),
                properties.getOpenAiCompatible().getDefaultChatModel(),
                properties.getOpenAiCompatible().getDefaultEmbeddingModel(),
                properties.getOpenAiCompatible().getDefaultApiKeyEnv(),
                null,
                null,
                null,
                null,
                Map.of());
    }
}
