package com.uniovi.rag.application.service.llm.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.OllamaModelAvailabilityPort;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogSource;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogModelDto;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogResponseDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmCatalogApiServiceOpenAiProviderTest {

    private OllamaModelAvailabilityPort ollamaAvailability;
    private LlmCatalogApiService apiService;

    @BeforeEach
    void setUp() {
        LlmProperties properties = new LlmProperties();
        LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://litellm:4000");
        openAi.setDefaultChatModel("gpt-oss:20b");
        openAi.setAvailableChatModels(List.of("gpt-oss:20b"));
        openAi.setDefaultEmbeddingModel("text-embedding-3-small");
        openAi.setAvailableEmbeddingModels(List.of("text-embedding-3-small"));

        ollamaAvailability = mock(OllamaModelAvailabilityPort.class);
        when(ollamaAvailability.isModelPresent(anyString())).thenThrow(new RuntimeException("Ollama unreachable"));

        apiService =
                new LlmCatalogApiService(
                        new LlmModelCatalogService(properties),
                        ollamaAvailability,
                        new RagVectorProperties(1024, true));
    }

    @Test
    void openAiCompatibleCatalog_loadsWithoutOllamaProbe() {
        LlmCatalogResponseDto response =
                apiService.listCatalog(LlmProvider.OPENAI_COMPATIBLE, LlmModelCapability.CHAT, null, true);

        assertThat(response.models()).extracting(LlmCatalogModelDto::modelName).containsExactly("gpt-oss:20b");
        LlmCatalogModelDto row = response.models().getFirst();
        assertThat(row.provider()).isEqualTo(LlmProvider.OPENAI_COMPATIBLE);
        assertThat(row.source()).isEqualTo(LlmCatalogSource.LITELLM_CONFIGURED);
        assertThat(row.runtimeStatus()).isEqualTo(LlmCatalogRuntimeStatus.NOT_PROBED);
        assertThat(row.runtimeDetail()).contains("not probed");
        verify(ollamaAvailability, never()).isModelPresent(anyString());
    }

    @Test
    void ollamaNativeInstalledModel_showsOllamaLiveSource() {
        LlmProperties properties = new LlmProperties();
        properties.getOllama().setDefaultChatModel("gemma3:4b");
        properties.getOllama().setAvailableChatModels(List.of("gemma3:4b"));
        OllamaModelAvailabilityPort ollamaOnly = mock(OllamaModelAvailabilityPort.class);
        when(ollamaOnly.isModelPresent("gemma3:4b")).thenReturn(true);

        LlmCatalogApiService ollamaService =
                new LlmCatalogApiService(
                        new LlmModelCatalogService(properties),
                        ollamaOnly,
                        new RagVectorProperties(1024, true));

        LlmCatalogResponseDto response =
                ollamaService.listCatalog(LlmProvider.OLLAMA_NATIVE, LlmModelCapability.CHAT, null, true);

        assertThat(response.models().getFirst().source()).isEqualTo(LlmCatalogSource.OLLAMA_LIVE);
        assertThat(response.models().getFirst().runtimeStatus()).isEqualTo(LlmCatalogRuntimeStatus.AVAILABLE);
    }
}
