package com.uniovi.rag.application.service.llm.catalog;

import com.uniovi.rag.application.port.llm.catalog.LlmModelCatalogPort;
import com.uniovi.rag.application.service.knowledge.IndexProfileJsonSupport;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogEntry;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogQuery;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Maps short or legacy embedding model ids (e.g. {@code mxbai-embed-large} from DB defaults) to a configured
 * catalog entry for the effective provider.
 */
@Service
public class EmbeddingModelCatalogResolver {

    private final LlmModelCatalogPort modelCatalog;
    private final LlmProperties llmProperties;

    public EmbeddingModelCatalogResolver(LlmModelCatalogPort modelCatalog, LlmProperties llmProperties) {
        this.modelCatalog = Objects.requireNonNull(modelCatalog, "modelCatalog");
        this.llmProperties = Objects.requireNonNull(llmProperties, "llmProperties");
    }

    public String resolveForEffectiveProvider(String requestedModelId) {
        LlmProvider provider = llmProperties.getEffectiveDefaultEmbeddingProvider();
        return resolve(provider, requestedModelId);
    }

    public String resolve(LlmProvider provider, String requestedModelId) {
        LlmProvider effectiveProvider = provider != null ? provider : llmProperties.getEffectiveDefaultEmbeddingProvider();
        if (requestedModelId == null || requestedModelId.isBlank()) {
            return llmProperties.effectiveDefaultEmbeddingModel();
        }
        String trimmed = requestedModelId.trim();
        List<LlmCatalogEntry> entries =
                modelCatalog.listConfigured(
                        new LlmCatalogQuery(effectiveProvider, LlmModelCapability.EMBEDDING, null, null));
        for (LlmCatalogEntry entry : entries) {
            if (!entry.available()) {
                continue;
            }
            if (entry.modelName().equals(trimmed)) {
                return entry.modelName();
            }
        }
        String normalized = IndexProfileJsonSupport.normalizeEmbeddingKey(trimmed);
        for (LlmCatalogEntry entry : entries) {
            if (!entry.available()) {
                continue;
            }
            if (IndexProfileJsonSupport.normalizeEmbeddingKey(entry.modelName()).equals(normalized)) {
                return entry.modelName();
            }
        }
        return trimmed;
    }
}
