package com.uniovi.rag.application.service.model;

import com.uniovi.rag.application.port.ModelCatalogPort;
import com.uniovi.rag.application.port.llm.catalog.LlmModelCatalogPort;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Dynamic model governance: allow catalog-known models unless explicitly blocked in {@code allowed_model}.
 */
@Service
public class ModelGovernanceService {

    private final ModelCatalogPort modelCatalogPort;
    private final LlmModelCatalogPort llmModelCatalogPort;

    public ModelGovernanceService(ModelCatalogPort modelCatalogPort, LlmModelCatalogPort llmModelCatalogPort) {
        this.modelCatalogPort = modelCatalogPort;
        this.llmModelCatalogPort = llmModelCatalogPort;
    }

    public void assertChatModelAllowed(LlmProvider provider, String modelName) {
        validate(provider, modelName, LlmModelCapability.CHAT, modelCatalogPort.blockedLlmNamesInGovernance());
    }

    public void assertEmbeddingModelAllowed(LlmProvider provider, String modelName) {
        validate(
                provider,
                modelName,
                LlmModelCapability.EMBEDDING,
                modelCatalogPort.blockedEmbeddingNamesInGovernance());
    }

    public boolean isChatModelGovernanceAllowed(LlmProvider provider, String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        String trimmed = modelName.trim();
        return isInCatalog(provider, trimmed, LlmModelCapability.CHAT)
                && !isBlocked(trimmed, modelCatalogPort.blockedLlmNamesInGovernance());
    }

    public boolean isEmbeddingModelGovernanceAllowed(LlmProvider provider, String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        String trimmed = modelName.trim();
        return isInCatalog(provider, trimmed, LlmModelCapability.EMBEDDING)
                && !isBlocked(trimmed, modelCatalogPort.blockedEmbeddingNamesInGovernance());
    }

    public boolean isKnownChatModel(LlmProvider provider, String modelName) {
        if (modelName == null || modelName.isBlank() || provider == null) {
            return false;
        }
        return isInCatalog(provider, modelName.trim(), LlmModelCapability.CHAT);
    }

    public boolean isKnownEmbeddingModel(LlmProvider provider, String modelName) {
        if (modelName == null || modelName.isBlank() || provider == null) {
            return false;
        }
        return isInCatalog(provider, modelName.trim(), LlmModelCapability.EMBEDDING);
    }

    public boolean isChatModelBlocked(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        return isBlocked(modelName.trim(), modelCatalogPort.blockedLlmNamesInGovernance());
    }

    public boolean isEmbeddingModelBlocked(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        return isBlocked(modelName.trim(), modelCatalogPort.blockedEmbeddingNamesInGovernance());
    }

    private void validate(
            LlmProvider provider, String modelName, LlmModelCapability capability, Set<String> blocked) {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException(capability.name() + " model override is empty");
        }
        String trimmed = modelName.trim();
        if (isBlocked(trimmed, blocked)) {
            throw new IllegalArgumentException(
                    capabilityLabel(capability) + " model is blocked by governance: " + trimmed);
        }
        if (!isInCatalog(provider, trimmed, capability)) {
            throw new IllegalArgumentException(
                    capabilityLabel(capability) + " model is not allowed by governance: " + trimmed);
        }
    }

    private boolean isInCatalog(LlmProvider provider, String modelName, LlmModelCapability capability) {
        if (provider == null) {
            return false;
        }
        return llmModelCatalogPort.find(provider, modelName, capability).isPresent();
    }

    private static boolean isBlocked(String modelName, Set<String> blocked) {
        return blocked != null && blocked.contains(modelName);
    }

    private static String capabilityLabel(LlmModelCapability capability) {
        return capability == LlmModelCapability.EMBEDDING ? "Embedding" : "LLM";
    }
}
