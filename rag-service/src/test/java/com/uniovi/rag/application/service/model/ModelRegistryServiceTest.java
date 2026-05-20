package com.uniovi.rag.application.service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.product.ModelRegistryAvailabilityStatus;
import com.uniovi.rag.domain.product.ProductDemoModel;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.interfaces.rest.dto.modelregistry.ModelRegistryResponseDto;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ModelRegistryServiceTest {

    @Mock
    private OllamaApiClient ollamaApiClient;

    @InjectMocks
    private ModelRegistryService modelRegistryService;

    @Test
    void snapshot_whenOllamaDown_marksRowsError() throws Exception {
        when(ollamaApiClient.listModelNames()).thenThrow(new IOException("connection refused"));

        ModelRegistryResponseDto dto = modelRegistryService.snapshot();

        assertThat(dto.ollamaReachable()).isFalse();
        assertThat(dto.ollamaErrorMessage()).contains("connection refused");
        assertThat(dto.llmModels()).hasSize(3);
        assertThat(dto.embeddingModels()).hasSize(3);
        assertThat(dto.llmModels()).allMatch(r -> r.status() == ModelRegistryAvailabilityStatus.ERROR);
    }

    @Test
    void snapshot_whenInstalled_setsAvailable() throws Exception {
        when(ollamaApiClient.listModelNames())
                .thenReturn(Set.of("gemma3:4b", "mxbai-embed-large:latest"));

        ModelRegistryResponseDto dto = modelRegistryService.snapshot();

        assertThat(dto.ollamaReachable()).isTrue();
        assertThat(dto.llmModels().stream().filter(r -> r.modelId().equals("gemma3:4b")).findFirst().orElseThrow()
                        .status())
                .isEqualTo(ModelRegistryAvailabilityStatus.AVAILABLE);
        assertThat(dto.embeddingModels().stream()
                        .filter(r -> r.modelId().equals("mxbai-embed-large"))
                        .findFirst()
                        .orElseThrow()
                        .status())
                .isEqualTo(ModelRegistryAvailabilityStatus.AVAILABLE);
    }

    @Test
    void check_unknownModel_returns400() {
        assertThatThrownBy(() -> modelRegistryService.check("evil-model:latest", true))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void assertPullAllowed_acceptsCuratedId() {
        modelRegistryService.assertPullAllowed(ProductDemoModel.LLAMA3_1_8B.modelId());
    }

    @Test
    void assertPullAllowed_rejectsUnknown() {
        assertThatThrownBy(() -> modelRegistryService.assertPullAllowed("phi3"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }
}
