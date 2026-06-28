package com.uniovi.rag.application.service.model;

import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.persistence.AllowedModelRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import com.uniovi.rag.interfaces.rest.dto.ModelsCatalogResponseDto;
import com.uniovi.rag.interfaces.rest.dto.SelectableModelDto;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelsCatalogServiceTest {

    @Mock
    private AllowedModelRepository allowedModelRepository;

    @Mock
    private OllamaApiClient ollamaApiClient;

    @Mock
    private EmbeddingModelStoreCompatibility embeddingModelStoreCompatibility;

    @InjectMocks
    private ModelsCatalogService modelsCatalogService;

    @Test
    void buildCatalog_mergesAllowlistAndInstalled() throws Exception {
        AllowedModelEntity row = mock(AllowedModelEntity.class);
        when(row.getName()).thenReturn("m:latest");
        when(row.getType()).thenReturn(AllowedModelType.LLM);
        when(row.isInAllowlist()).thenReturn(true);

        when(allowedModelRepository.findAll()).thenReturn(List.of(row));
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of("m:latest", "other:1"));

        ModelsCatalogResponseDto dto = modelsCatalogService.buildCatalog();

        assertThat(dto.ollamaReachable()).isTrue();
        assertThat(dto.installedModelNames()).containsExactly("m:latest", "other:1");
        assertThat(dto.allowlist()).hasSize(1);
        assertThat(dto.allowlist().getFirst().installedInOllama()).isTrue();
    }

    @Test
    void buildCatalog_whenOllamaFails_marksUnreachable() throws Exception {
        when(allowedModelRepository.findAll()).thenReturn(List.of());
        when(ollamaApiClient.listModelNames()).thenThrow(new IOException("down"));

        ModelsCatalogResponseDto dto = modelsCatalogService.buildCatalog();

        assertThat(dto.ollamaReachable()).isFalse();
        assertThat(dto.installedModelNames()).isEmpty();
    }

    @Test
    void listSelectableByType_embedding_returnsOnlyStoreCompatibleInstalledModels() throws Exception {
        AllowedModelEntity mxbai = embeddingRow("mxbai-embed-large:latest");
        AllowedModelEntity nomic = embeddingRow("nomic-embed-text:latest");
        AllowedModelEntity qwen = embeddingRow("qwen3-embedding:latest");

        when(allowedModelRepository.findAll()).thenReturn(List.of(mxbai, nomic, qwen));
        when(ollamaApiClient.listModelNames())
                .thenReturn(Set.of("mxbai-embed-large:latest", "nomic-embed-text:latest", "qwen3-embedding:latest"));
        when(embeddingModelStoreCompatibility.isSelectableForLabEmbeddingBenchmark("mxbai-embed-large:latest"))
                .thenReturn(true);
        when(embeddingModelStoreCompatibility.isSelectableForLabEmbeddingBenchmark("nomic-embed-text:latest"))
                .thenReturn(false);
        when(embeddingModelStoreCompatibility.isSelectableForLabEmbeddingBenchmark("qwen3-embedding:latest"))
                .thenReturn(false);

        List<SelectableModelDto> rows = modelsCatalogService.listSelectableByType("EMBEDDING");

        assertThat(rows).extracting(SelectableModelDto::modelId).containsExactly("mxbai-embed-large:latest");
    }

    @Test
    void listSelectableByType_embedding_doesNotRequireBgeM3() throws Exception {
        AllowedModelEntity mxbai = embeddingRow("mxbai-embed-large:latest");

        when(allowedModelRepository.findAll()).thenReturn(List.of(mxbai));
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of("mxbai-embed-large:latest"));
        when(embeddingModelStoreCompatibility.isSelectableForLabEmbeddingBenchmark(anyString())).thenReturn(true);

        List<SelectableModelDto> rows = modelsCatalogService.listSelectableByType("EMBEDDING");

        assertThat(rows).extracting(SelectableModelDto::modelId).containsExactly("mxbai-embed-large:latest");
    }

    private static AllowedModelEntity embeddingRow(String name) {
        AllowedModelEntity row = mock(AllowedModelEntity.class);
        when(row.getName()).thenReturn(name);
        when(row.getType()).thenReturn(AllowedModelType.EMBEDDING);
        when(row.isInAllowlist()).thenReturn(true);
        return row;
    }
}
