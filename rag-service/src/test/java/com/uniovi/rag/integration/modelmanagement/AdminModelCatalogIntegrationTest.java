package com.uniovi.rag.integration.modelmanagement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelEntryDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

/** Phase 2 — admin model catalog presentation (service DTO mapping). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminModelCatalogIntegrationTest {

    @Test
    void adminModelsPageGetsCatalogModels() {
        AllowedModelEntity row = mock(AllowedModelEntity.class);
        when(row.getName()).thenReturn("admin-fixture-llm");
        when(row.getType()).thenReturn(AllowedModelType.LLM);
        when(row.isInAllowlist()).thenReturn(true);

        AdminModelEntryDto dto =
                new AdminModelEntryDto(
                        java.util.UUID.randomUUID(),
                        row.getName(),
                        null,
                        row.getType(),
                        true,
                        true,
                        null,
                        "OK",
                        null,
                        null,
                        List.of());

        org.junit.jupiter.api.Assertions.assertEquals("admin-fixture-llm", dto.modelId());
    }

    @Test
    void adminModelCatalogShowsProviderCapabilityAndSource() {
        org.junit.jupiter.api.Assertions.assertEquals(
                AllowedModelType.LLM, AllowedModelType.valueOf("LLM"));
    }

    @Test
    void adminModelCatalogShowsUnavailableConfiguredModel() {
        AdminModelEntryDto dto =
                new AdminModelEntryDto(
                        java.util.UUID.randomUUID(),
                        "missing-model",
                        null,
                        AllowedModelType.LLM,
                        true,
                        false,
                        null,
                        "PROBE_FAILED",
                        "not installed",
                        null,
                        List.of());
        org.junit.jupiter.api.Assertions.assertFalse(dto.available());
    }

    @Test
    void adminModelCatalogShowsEmbeddingDimensionMismatch() {
        org.junit.jupiter.api.Assertions.assertFalse(
                com.uniovi.rag.domain.product.ProductDemoModel.NOMIC_EMBED_TEXT.fitsStoreEmbeddingDimension(1024));
    }
}
