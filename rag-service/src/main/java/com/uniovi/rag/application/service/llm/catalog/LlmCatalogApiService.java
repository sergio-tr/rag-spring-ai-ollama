package com.uniovi.rag.application.service.llm.catalog;

import com.uniovi.rag.application.port.OllamaModelAvailabilityPort;
import com.uniovi.rag.application.port.llm.catalog.LlmModelCatalogPort;
import com.uniovi.rag.configuration.RagVectorProperties;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogEntry;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogQuery;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogSource;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.product.ProductDemoModel;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogModelDto;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogResponseDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;

/** Properties-backed LLM catalog for REST consumers (Phase 1). */
@Service
public class LlmCatalogApiService {

    private final LlmModelCatalogPort modelCatalog;
    private final OllamaModelAvailabilityPort ollamaModelAvailability;
    private final RagVectorProperties ragVectorProperties;

    public LlmCatalogApiService(
            LlmModelCatalogPort modelCatalog,
            OllamaModelAvailabilityPort ollamaModelAvailability,
            RagVectorProperties ragVectorProperties) {
        this.modelCatalog = modelCatalog;
        this.ollamaModelAvailability = ollamaModelAvailability;
        this.ragVectorProperties = ragVectorProperties;
    }

    public LlmCatalogResponseDto listCatalog(
            LlmProvider provider,
            LlmModelCapability capability,
            Boolean selectableByUser,
            boolean includeRuntimeStatus) {
        LlmCatalogQuery query =
                new LlmCatalogQuery(provider, capability, selectableByUser, null);
        List<LlmCatalogModelDto> models = new ArrayList<>();
        for (LlmCatalogEntry entry : modelCatalog.listConfigured(query)) {
            models.add(toDto(entry, includeRuntimeStatus));
        }
        models.sort(
                Comparator.comparing(LlmCatalogModelDto::provider)
                        .thenComparing(LlmCatalogModelDto::capability)
                        .thenComparing(LlmCatalogModelDto::modelName));
        return new LlmCatalogResponseDto(List.copyOf(models));
    }

    private LlmCatalogModelDto toDto(LlmCatalogEntry entry, boolean includeRuntimeStatus) {
        RuntimeProbe probe = resolveRuntimeStatus(entry, includeRuntimeStatus);
        Integer embeddingDimensions = null;
        Boolean compatibleWithStore = null;
        if (entry.capability() == LlmModelCapability.EMBEDDING) {
            var dims = resolveEmbeddingDimensions(entry.modelName());
            embeddingDimensions = dims.isPresent() ? dims.getAsInt() : null;
            compatibleWithStore =
                    embeddingDimensions != null
                            ? embeddingDimensions == ragVectorProperties.storeEmbeddingDimension()
                            : null;
        }
        return new LlmCatalogModelDto(
                entry.provider(),
                entry.modelName(),
                entry.capability(),
                entry.available(),
                entry.selectableByUser(),
                entry.usableAsDefault(),
                probe.status(),
                probe.detail(),
                embeddingDimensions,
                compatibleWithStore,
                resolveDisplaySource(entry, probe));
    }

    private static LlmCatalogSource resolveDisplaySource(LlmCatalogEntry entry, RuntimeProbe probe) {
        if (entry.provider() == LlmProvider.OLLAMA_NATIVE && probe.status() == LlmCatalogRuntimeStatus.AVAILABLE) {
            return LlmCatalogSource.OLLAMA_LIVE;
        }
        if (entry.source() == LlmCatalogSource.PROPERTIES) {
            return entry.provider() == LlmProvider.OPENAI_COMPATIBLE
                    ? LlmCatalogSource.LITELLM_CONFIGURED
                    : LlmCatalogSource.CONFIGURED_CATALOG;
        }
        return entry.source();
    }

    private RuntimeProbe resolveRuntimeStatus(LlmCatalogEntry entry, boolean includeRuntimeStatus) {
        if (!includeRuntimeStatus) {
            return new RuntimeProbe(LlmCatalogRuntimeStatus.UNKNOWN, null);
        }
        if (entry.provider() != LlmProvider.OLLAMA_NATIVE) {
            return new RuntimeProbe(
                    LlmCatalogRuntimeStatus.NOT_PROBED,
                    "Configured in catalog; remote provider runtime not probed");
        }
        try {
            boolean present = ollamaModelAvailability.isModelPresent(entry.modelName());
            if (present) {
                return new RuntimeProbe(LlmCatalogRuntimeStatus.AVAILABLE, null);
            }
            return new RuntimeProbe(LlmCatalogRuntimeStatus.UNAVAILABLE, "Model not installed locally in Ollama");
        } catch (RuntimeException e) {
            return new RuntimeProbe(LlmCatalogRuntimeStatus.PROBE_FAILED, e.getMessage());
        }
    }

    public static OptionalInt resolveEmbeddingDimensions(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return OptionalInt.empty();
        }
        Optional<ProductDemoModel> curated = ProductDemoModel.resolve(modelName.trim());
        if (curated.isEmpty()) {
            int colon = modelName.lastIndexOf(':');
            if (colon > 0) {
                curated = ProductDemoModel.resolve(modelName.substring(0, colon).trim());
            }
        }
        if (curated.isPresent() && curated.get().modelType() == AllowedModelType.EMBEDDING) {
            return curated.get().documentedOutputDimensions();
        }
        String lower = modelName.toLowerCase(Locale.ROOT);
        if (lower.contains("mxbai-embed")) {
            return OptionalInt.of(1024);
        }
        if (lower.contains("bge-m3")) {
            return OptionalInt.of(1024);
        }
        if (lower.contains("snowflake-arctic-embed")) {
            return OptionalInt.of(1024);
        }
        if (lower.contains("nomic-embed")) {
            return OptionalInt.of(768);
        }
        return OptionalInt.empty();
    }

    private record RuntimeProbe(LlmCatalogRuntimeStatus status, String detail) {}
}
