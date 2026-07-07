package com.uniovi.rag.application.service.llm;

import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.catalog.LlmModelCatalogPort;
import com.uniovi.rag.domain.llm.LlmGenerationParameterId;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.LlmProviderParameterSupport;
import com.uniovi.rag.domain.llm.LlmRoutingBackend;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Drops generation parameters unsupported by the effective provider/backend before HTTP mapping.
 * Effective configuration values remain unchanged upstream; only the outgoing request payload is filtered.
 */
@Service
public class LlmProviderParameterFilter {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderParameterFilter.class);

    private final LlmModelCatalogPort modelCatalog;

    public LlmProviderParameterFilter(LlmModelCatalogPort modelCatalog) {
        this.modelCatalog = modelCatalog;
    }

    public LlmRoutingBackend resolveBackend(LlmProvider provider, String modelId) {
        if (provider == LlmProvider.OLLAMA_NATIVE) {
            return LlmRoutingBackend.OLLAMA;
        }
        if (modelId != null && !modelId.isBlank()) {
            String trimmed = modelId.trim();
            if (modelCatalog
                    .find(LlmProvider.OLLAMA_NATIVE, trimmed, LlmModelCapability.CHAT)
                    .isPresent()) {
                return LlmRoutingBackend.OLLAMA;
            }
            if (LlmProviderParameterSupport.isOllamaStyleModelName(trimmed)) {
                return LlmRoutingBackend.OLLAMA;
            }
        }
        return LlmRoutingBackend.OPENAI_COMPATIBLE_API;
    }

    public LlmChatRequest filterChatRequest(LlmChatRequest request, LlmProvider provider) {
        LlmRoutingBackend backend = resolveBackend(provider, request.model());
        Map<String, Object> filteredAdditional = filterAdditionalParameters(backend, request.model(), request.additionalParameters());
        if (filteredAdditional.equals(request.additionalParameters())) {
            return request;
        }
        return new LlmChatRequest(
                request.model(),
                request.messages(),
                request.temperature(),
                request.timeoutMs(),
                filteredAdditional);
    }

    public Map<String, Object> filterAdditionalParameters(
            LlmProvider provider, String modelId, Map<String, Object> additionalParameters) {
        return filterAdditionalParameters(resolveBackend(provider, modelId), modelId, additionalParameters);
    }

    Map<String, Object> filterAdditionalParameters(
            LlmRoutingBackend backend, String modelId, Map<String, Object> additionalParameters) {
        if (additionalParameters == null || additionalParameters.isEmpty()) {
            additionalParameters = Map.of();
        }
        Map<String, Object> filtered = new LinkedHashMap<>(additionalParameters);
        List<String> dropped = new ArrayList<>();

        for (LlmGenerationParameterId parameter : LlmGenerationParameterId.values()) {
            if (isSupported(backend, parameter, modelId)) {
                continue;
            }
            for (String key : parameter.configKeys()) {
                if (filtered.remove(key) != null) {
                    dropped.add(key);
                }
            }
        }

        if (backend == LlmRoutingBackend.OLLAMA
                && LlmProviderParameterSupport.supportsThinkParameter(modelId)
                && !containsThink(filtered)) {
            filtered.put("think", Boolean.FALSE);
        }

        if (!dropped.isEmpty()) {
            log.debug(
                    "Dropped unsupported LLM parameters for backend={} model={}: {}",
                    backend,
                    modelId,
                    dropped);
        }
        return Map.copyOf(filtered);
    }

    public boolean isSupported(LlmRoutingBackend backend, LlmGenerationParameterId parameter, String modelId) {
        return LlmProviderParameterSupport.isSupported(backend, parameter, modelId);
    }

    public static boolean isUnsupportedParamsError(Throwable error) {
        if (error == null) {
            return false;
        }
        String message = error.getMessage();
        if (message != null) {
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("unsupportedparamserror") || lower.contains("does not support parameters")) {
                return true;
            }
        }
        return isUnsupportedParamsError(error.getCause());
    }

    private static boolean containsThink(Map<String, Object> parameters) {
        return parameters.containsKey("think");
    }
}
