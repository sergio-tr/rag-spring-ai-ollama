package com.uniovi.rag.service.model;

import com.uniovi.rag.interfaces.rest.dto.ModelsCatalogResponseDto;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.persistence.AllowedModelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelsCatalogServiceTest {

    @Mock
    private AllowedModelRepository allowedModelRepository;

    @Mock
    private OllamaApiClient ollamaApiClient;

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
        when(ollamaApiClient.listModelNames()).thenThrow(new java.io.IOException("down"));

        ModelsCatalogResponseDto dto = modelsCatalogService.buildCatalog();

        assertThat(dto.ollamaReachable()).isFalse();
        assertThat(dto.installedModelNames()).isEmpty();
    }
}
