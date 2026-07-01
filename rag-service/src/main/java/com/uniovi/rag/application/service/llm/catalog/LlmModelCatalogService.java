package com.uniovi.rag.application.service.llm.catalog;

import com.uniovi.rag.application.exception.llm.LlmConfigurationException;
import com.uniovi.rag.application.port.llm.catalog.LlmModelCatalogPort;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogDefaults;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogEntry;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogQuery;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogSource;
import com.uniovi.rag.domain.llm.catalog.LlmModelReasonCodes;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.llm.catalog.LlmModelRoleResolver;
import com.uniovi.rag.domain.llm.catalog.LlmModelUsageContext;
import com.uniovi.rag.infrastructure.llm.LlmOllamaDefaults;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Properties-backed LLM model catalog (G.2). */
@Service
public class LlmModelCatalogService implements LlmModelCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(LlmModelCatalogService.class);

    private final Map<CatalogKey, LlmCatalogEntry> entries;
    private final Map<LlmProvider, LlmCatalogDefaults> defaultsByProvider;

    public LlmModelCatalogService(LlmProperties properties) {
        Objects.requireNonNull(properties, "properties");
        Map<CatalogKey, LlmCatalogEntry> built = new LinkedHashMap<>();
        LlmOllamaDefaults ollama = properties.getOllama();
        LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();

        registerProviderModels(
                built,
                LlmProvider.OLLAMA_NATIVE,
                ollama.getAvailableChatModels(),
                ollama.getDefaultChatModel(),
                LlmModelCapability.CHAT);
        registerProviderModels(
                built,
                LlmProvider.OLLAMA_NATIVE,
                ollama.getAvailableEmbeddingModels(),
                ollama.getDefaultEmbeddingModel(),
                LlmModelCapability.EMBEDDING);

        registerProviderModels(
                built,
                LlmProvider.OPENAI_COMPATIBLE,
                openAi.getAvailableChatModels(),
                openAi.getDefaultChatModel(),
                LlmModelCapability.CHAT);
        registerProviderModels(
                built,
                LlmProvider.OPENAI_COMPATIBLE,
                openAi.getAvailableEmbeddingModels(),
                openAi.getDefaultEmbeddingModel(),
                LlmModelCapability.EMBEDDING);

        this.entries = Map.copyOf(built);
        this.defaultsByProvider =
                Map.of(
                        LlmProvider.OLLAMA_NATIVE,
                        new LlmCatalogDefaults(
                                LlmProvider.OLLAMA_NATIVE,
                                ollama.getDefaultChatModel(),
                                ollama.getDefaultEmbeddingModel()),
                        LlmProvider.OPENAI_COMPATIBLE,
                        new LlmCatalogDefaults(
                                LlmProvider.OPENAI_COMPATIBLE,
                                openAi.getDefaultChatModel(),
                                openAi.getDefaultEmbeddingModel()));
    }

    @Override
    public Optional<LlmCatalogEntry> find(
            LlmProvider provider, String modelName, LlmModelCapability capability) {
        if (provider == null || capability == null || modelName == null || modelName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(new CatalogKey(provider, modelName.trim(), capability)));
    }

    @Override
    public List<LlmCatalogEntry> listConfigured(LlmCatalogQuery query) {
        LlmCatalogQuery q = query != null ? query : LlmCatalogQuery.all();
        List<LlmCatalogEntry> out = new ArrayList<>();
        for (LlmCatalogEntry entry : entries.values()) {
            if (q.provider() != null && entry.provider() != q.provider()) {
                continue;
            }
            if (q.capability() != null && entry.capability() != q.capability()) {
                continue;
            }
            if (q.selectableByUser() != null && entry.selectableByUser() != q.selectableByUser()) {
                continue;
            }
            if (q.usableAsDefault() != null && entry.usableAsDefault() != q.usableAsDefault()) {
                continue;
            }
            out.add(entry);
        }
        return List.copyOf(out);
    }

    @Override
    public void assertUsable(
            LlmProvider provider,
            String modelName,
            LlmModelCapability capability,
            LlmModelUsageContext usageContext) {
        if (provider == null) {
            throw configurationError(null, modelName, capability, usageContext, "LLM provider must not be null");
        }
        if (capability == null) {
            throw configurationError(provider, modelName, null, usageContext, "Model capability must not be null");
        }
        if (modelName == null || modelName.isBlank()) {
            throw configurationError(
                    provider, modelName, capability, usageContext, "Model name must not be blank");
        }
        String normalized = modelName.trim();
        Optional<LlmCatalogEntry> entry = find(provider, normalized, capability);
        if (entry.isEmpty()) {
            LlmProvider otherProvider = oppositeProvider(provider);
            if (otherProvider != null && find(otherProvider, normalized, capability).isPresent()) {
                throw configurationError(
                        provider,
                        normalized,
                        capability,
                        usageContext,
                        crossProviderRejectionMessage(normalized, provider, otherProvider));
            }
            throw configurationError(
                    provider,
                    normalized,
                    capability,
                    usageContext,
                    "Model '"
                            + normalized
                            + "' is not registered for provider "
                            + provider
                            + " with capability "
                            + capability);
        }
        LlmCatalogEntry resolved = entry.get();
        if (!resolved.available()) {
            throw configurationError(
                    provider,
                    normalized,
                    capability,
                    usageContext,
                    "Model '" + normalized + "' is not available in the catalog");
        }
        if (usageContext == LlmModelUsageContext.USER_SELECTION && !resolved.selectableByUser()) {
            throw configurationError(
                    provider,
                    normalized,
                    capability,
                    usageContext,
                    "Model '" + normalized + "' is not selectable by users");
        }
        if (usageContext == LlmModelUsageContext.SYSTEM_DEFAULT && !resolved.usableAsDefault()) {
            throw configurationError(
                    provider,
                    normalized,
                    capability,
                    usageContext,
                    "Model '" + normalized + "' cannot be used as a system default");
        }
    }

    @Override
    public LlmCatalogDefaults resolveSystemDefaults(LlmProvider provider) {
        LlmProvider p = provider != null ? provider : LlmProvider.OLLAMA_NATIVE;
        return defaultsByProvider.getOrDefault(
                p,
                new LlmCatalogDefaults(p, null, null));
    }

    private static void registerProviderModels(
            Map<CatalogKey, LlmCatalogEntry> target,
            LlmProvider provider,
            List<String> configuredModels,
            String defaultModel,
            LlmModelCapability capability) {
        List<String> names = new ArrayList<>(configuredModels != null ? configuredModels : List.of());
        if (defaultModel != null && !defaultModel.isBlank()) {
            String trimmedDefault = defaultModel.trim();
            if (!names.contains(trimmedDefault)) {
                log.warn(
                        "Default {} model '{}' for provider {} is not listed in available-*-models; adding to catalog",
                        capability.name().toLowerCase(Locale.ROOT),
                        trimmedDefault,
                        provider);
                names.add(trimmedDefault);
            }
        }
        if (names.isEmpty() && defaultModel != null && !defaultModel.isBlank()) {
            names.add(defaultModel.trim());
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String trimmed = name.trim();
            boolean chatPrimaryCapable =
                    capability != LlmModelCapability.CHAT
                            || LlmModelRoleResolver.supportsPrimaryChat(trimmed, capability);
            boolean selectableByUser = capability == LlmModelCapability.CHAT && chatPrimaryCapable;
            boolean usableAsDefault =
                    defaultModel != null
                            && !defaultModel.isBlank()
                            && trimmed.equals(defaultModel.trim())
                            && chatPrimaryCapable;
            CatalogKey key = new CatalogKey(provider, trimmed, capability);
            target.put(
                    key,
                    new LlmCatalogEntry(
                            provider,
                            trimmed,
                            capability,
                            true,
                            selectableByUser,
                            usableAsDefault,
                            trimmed,
                            "",
                            provider == LlmProvider.OPENAI_COMPATIBLE
                                    ? LlmCatalogSource.LITELLM_CONFIGURED
                                    : LlmCatalogSource.CONFIGURED_CATALOG,
                            Map.of()));
        }
    }

    private static LlmProvider oppositeProvider(LlmProvider provider) {
        if (provider == LlmProvider.OLLAMA_NATIVE) {
            return LlmProvider.OPENAI_COMPATIBLE;
        }
        if (provider == LlmProvider.OPENAI_COMPATIBLE) {
            return LlmProvider.OLLAMA_NATIVE;
        }
        return null;
    }

    private static String crossProviderRejectionMessage(
            String modelName, LlmProvider requestedProvider, LlmProvider registeredProvider) {
        if (requestedProvider == LlmProvider.OPENAI_COMPATIBLE && registeredProvider == LlmProvider.OLLAMA_NATIVE) {
            return "Model '"
                    + modelName
                    + "' is registered for OLLAMA_NATIVE only (spring.ai.ollama.* / rag.llm.ollama.*); "
                    + "add it to rag.llm.openai-compatible.available-*-models to use it with OPENAI_COMPATIBLE";
        }
        if (requestedProvider == LlmProvider.OLLAMA_NATIVE && registeredProvider == LlmProvider.OPENAI_COMPATIBLE) {
            return "Model '"
                    + modelName
                    + "' is registered for OPENAI_COMPATIBLE only (LiteLLM / rag.llm.openai-compatible.*); "
                    + "add it to rag.llm.ollama.available-*-models to use it with OLLAMA_NATIVE";
        }
        return "Model '"
                + modelName
                + "' is registered for "
                + registeredProvider
                + " only and cannot be used with "
                + requestedProvider;
    }

    private static LlmConfigurationException configurationError(
            LlmProvider provider,
            String modelName,
            LlmModelCapability capability,
            LlmModelUsageContext usageContext,
            String message) {
        String detail =
                "capability="
                        + capability
                        + ", usageContext="
                        + usageContext
                        + " (model catalog strict validation)";
        return LlmConfigurationException.invalidField(
                provider,
                "catalog",
                modelName != null ? modelName.trim() : null,
                null,
                LlmModelReasonCodes.format(resolveConfigurationReasonCode(message), message + " [" + detail + "]"));
    }

    private static String resolveConfigurationReasonCode(String message) {
        if (message != null && message.contains("not available in the catalog")) {
            return LlmModelReasonCodes.LLM_MODEL_UNAVAILABLE;
        }
        return LlmModelReasonCodes.LLM_MODEL_NOT_CONFIGURED;
    }

    private record CatalogKey(LlmProvider provider, String modelName, LlmModelCapability capability) {}
}
