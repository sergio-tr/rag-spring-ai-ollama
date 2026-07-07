package com.uniovi.rag.integration.modelmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.model.EmbeddingModelStoreCompatibility;
import com.uniovi.rag.application.service.model.ModelsCatalogService;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.persistence.AllowedModelRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import com.uniovi.rag.interfaces.rest.dto.SelectableModelDto;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Phase 2 - selectable models API service wiring. */
@ExtendWith(MockitoExtension.class)
class SelectableModelsIntegrationTest {

    @Mock private AllowedModelRepository allowedModelRepository;
    @Mock private OllamaApiClient ollamaApiClient;

    private ModelsCatalogService modelsCatalogService;

    @BeforeEach
    void setUp() {
        modelsCatalogService =
                new ModelsCatalogService(
                        allowedModelRepository,
                        ollamaApiClient,
                        new EmbeddingModelStoreCompatibility(new RagVectorProperties(1024, true)));
    }

    @Test
    void openAiUserSelectableModelsOnlyIncludeOpenAiChatModels() throws Exception {
        AllowedModelEntity row = llmRow("gpt-oss:20b");
        when(allowedModelRepository.findAll()).thenReturn(List.of(row));
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of("gpt-oss:20b", "gemma3:4b"));

        List<SelectableModelDto> rows = modelsCatalogService.listSelectableByType("LLM");

        assertThat(rows).extracting(SelectableModelDto::modelId).containsExactly("gpt-oss:20b");
    }

    @Test
    void ollamaUserSelectableModelsOnlyIncludeOllamaChatModels() throws Exception {
        AllowedModelEntity row = llmRow("ollama-chat-fixture");
        when(allowedModelRepository.findAll()).thenReturn(List.of(row));
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of("ollama-chat-fixture"));

        List<SelectableModelDto> rows = modelsCatalogService.listSelectableByType("LLM");

        assertThat(rows).extracting(SelectableModelDto::modelId).containsExactly("ollama-chat-fixture");
    }

    @Test
    void selectableModelsExcludeEmbeddingModels() throws Exception {
        AllowedModelEntity chat = llmRow("chat-only");
        AllowedModelEntity embed = embeddingRow("embed-only");
        when(allowedModelRepository.findAll()).thenReturn(List.of(chat, embed));
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of("chat-only", "embed-only"));

        List<SelectableModelDto> rows = modelsCatalogService.listSelectableByType("LLM");

        assertThat(rows).extracting(SelectableModelDto::modelId).containsExactly("chat-only");
    }

    @Test
    void unavailableConfiguredModelIsReturnedDisabledWithReason() throws Exception {
        AllowedModelEntity row = llmRow("missing-runtime-model");
        when(allowedModelRepository.findAll()).thenReturn(List.of(row));
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of());

        List<SelectableModelDto> rows = modelsCatalogService.listSelectableByType("LLM");

        assertThat(rows).isEmpty();
    }

    @Test
    void selectedModelNotConfiguredReturnsValidationError() throws Exception {
        AllowedModelEntity row = embeddingRow("nomic-embed-text:latest");
        when(allowedModelRepository.findAll()).thenReturn(List.of(row));
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of("nomic-embed-text:latest"));

        List<SelectableModelDto> rows = modelsCatalogService.listSelectableByType("EMBEDDING");

        assertThat(rows).isEmpty();
    }

    private static AllowedModelEntity llmRow(String name) {
        AllowedModelEntity row = mock(AllowedModelEntity.class);
        lenient().when(row.getName()).thenReturn(name);
        lenient().when(row.getType()).thenReturn(AllowedModelType.LLM);
        lenient().when(row.isInAllowlist()).thenReturn(true);
        return row;
    }

    private static AllowedModelEntity embeddingRow(String name) {
        AllowedModelEntity row = mock(AllowedModelEntity.class);
        lenient().when(row.getName()).thenReturn(name);
        lenient().when(row.getType()).thenReturn(AllowedModelType.EMBEDDING);
        lenient().when(row.isInAllowlist()).thenReturn(true);
        return row;
    }
}
