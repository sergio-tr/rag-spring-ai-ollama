package com.uniovi.rag.application.service.chat;

import com.uniovi.rag.interfaces.rest.dto.DisabledRuntimeFeatureDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeSnapshotCapabilitiesDto;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Materialization-aware Chat feature toggle gates (product closure: STRUCTURED_SEARCH = direct LLM + orchestration only).
 */
public final class MaterializationFeatureGateService {

    public static final String STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED = "STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED";
    public static final String STRUCTURED_SEARCH_FULL_CONTEXT_UNSUPPORTED = "STRUCTURED_SEARCH_FULL_CONTEXT_UNSUPPORTED";
    public static final String STRUCTURED_SEARCH_ADVANCED_RETRIEVAL_UNSUPPORTED =
            "STRUCTURED_SEARCH_ADVANCED_RETRIEVAL_UNSUPPORTED";

    public static final String MSG_STRUCTURED_SEARCH_RETRIEVAL =
            "Structured-search projects do not support vector retrieval.";
    public static final String MSG_STRUCTURED_SEARCH_FULL_CONTEXT =
            "Full-context mode is unavailable because this project has no vector chunks.";
    public static final String MSG_STRUCTURED_SEARCH_ADVANCED_RETRIEVAL =
            "Advisor/ranker/post-retrieval require vector retrieval and are unavailable for structured-search projects.";

    private static final Set<String> STRUCTURED_SEARCH_DISABLED_KEYS =
            Set.of(
                    "useRetrieval",
                    "naiveFullCorpusInPromptEnabled",
                    "useAdvisor",
                    "rankerEnabled",
                    "postRetrievalEnabled");

    private MaterializationFeatureGateService() {}

    public static Optional<DisabledRuntimeFeatureDto> structuredSearchDisable(String featureKey) {
        if (featureKey == null || !STRUCTURED_SEARCH_DISABLED_KEYS.contains(featureKey)) {
            return Optional.empty();
        }
        return Optional.of(
                switch (featureKey) {
                    case "useRetrieval" ->
                            new DisabledRuntimeFeatureDto(
                                    featureKey, STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED, MSG_STRUCTURED_SEARCH_RETRIEVAL);
                    case "naiveFullCorpusInPromptEnabled" ->
                            new DisabledRuntimeFeatureDto(
                                    featureKey,
                                    STRUCTURED_SEARCH_FULL_CONTEXT_UNSUPPORTED,
                                    MSG_STRUCTURED_SEARCH_FULL_CONTEXT);
                    case "useAdvisor", "rankerEnabled", "postRetrievalEnabled" ->
                            new DisabledRuntimeFeatureDto(
                                    featureKey,
                                    STRUCTURED_SEARCH_ADVANCED_RETRIEVAL_UNSUPPORTED,
                                    MSG_STRUCTURED_SEARCH_ADVANCED_RETRIEVAL);
                    default -> throw new IllegalStateException("unexpected key: " + featureKey);
                });
    }

    public static Optional<DisabledRuntimeFeatureDto> materializationDisable(
            String featureKey, RuntimeIndexCompatibilityDto indexCompatibility) {
        if (!isStructuredSearch(resolveActiveMaterializationStrategy(indexCompatibility))) {
            return Optional.empty();
        }
        return structuredSearchDisable(featureKey);
    }

    public static boolean isStructuredSearch(String materializationStrategy) {
        return materializationStrategy != null
                && "STRUCTURED_SEARCH".equalsIgnoreCase(materializationStrategy.trim());
    }

    public static String resolveActiveMaterializationStrategy(RuntimeIndexCompatibilityDto indexCompatibility) {
        if (indexCompatibility == null) {
            return null;
        }
        RuntimeSnapshotCapabilitiesDto caps = indexCompatibility.activeSnapshotCapabilities();
        if (caps != null
                && caps.materializationStrategy() != null
                && !caps.materializationStrategy().isBlank()) {
            return caps.materializationStrategy().trim();
        }
        Map<String, Object> profile = indexCompatibility.activeIndexProfile();
        if (profile != null) {
            Object raw = profile.get("materializationStrategy");
            if (raw instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        return null;
    }
}
