package com.uniovi.rag.testsupport.llm;

import static org.mockito.Mockito.mock;

import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.application.port.OllamaModelAvailabilityPort;
import com.uniovi.rag.application.port.llm.catalog.LlmModelCatalogPort;
import com.uniovi.rag.application.service.llm.catalog.LlmCatalogApiService;
import com.uniovi.rag.application.service.model.ModelGovernanceService;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.infrastructure.health.RagHealthProperties;

/** Constructs {@link LlmCatalogApiService} for unit/integration tests. */
public final class LlmCatalogApiServiceTestSupport {

    private LlmCatalogApiServiceTestSupport() {}

    public static LlmCatalogApiService service(
            LlmModelCatalogPort modelCatalog,
            OllamaModelAvailabilityPort ollamaAvailability,
            RagVectorProperties vectorProperties) {
        return service(modelCatalog, ollamaAvailability, vectorProperties, mock(ModelCatalogPort.class), true);
    }

    public static LlmCatalogApiService service(
            LlmModelCatalogPort modelCatalog,
            OllamaModelAvailabilityPort ollamaAvailability,
            RagVectorProperties vectorProperties,
            ModelCatalogPort modelCatalogPort,
            boolean ollamaVerifyModels) {
        RagHealthProperties health = new RagHealthProperties();
        health.setOllamaVerifyModels(ollamaVerifyModels);
        ModelGovernanceService governance = new ModelGovernanceService(modelCatalogPort, modelCatalog);
        return new LlmCatalogApiService(
                modelCatalog, ollamaAvailability, vectorProperties, governance, health);
    }
}
