package com.uniovi.rag.domain.runtime;

import com.uniovi.rag.configuration.RagFeatureConfiguration;

/**
 * Effective RAG feature flags for the current request: {@link RagExecutionContextHolder}
 * {@linkplain RagExecutionContext#resolvedConfig() resolved config} when set (chat / scoped pipeline),
 * otherwise the global {@link RagFeatureConfiguration} bean.
 */
public final class RagEffectiveFeatures {

    private RagEffectiveFeatures() {
    }

    public static boolean expansionEnabled(RagFeatureConfiguration global) {
        return flag(global, RagConfig::expansionEnabled, RagFeatureConfiguration::isExpansionEnabled);
    }

    public static boolean nerEnabled(RagFeatureConfiguration global) {
        return flag(global, RagConfig::nerEnabled, RagFeatureConfiguration::isNerEnabled);
    }

    public static boolean toolsEnabled(RagFeatureConfiguration global) {
        return flag(global, RagConfig::toolsEnabled, RagFeatureConfiguration::isToolsEnabled);
    }

    public static boolean metadataEnabled(RagFeatureConfiguration global) {
        return flag(global, RagConfig::metadataEnabled, RagFeatureConfiguration::isMetadataEnabled);
    }

    public static boolean reasoningEnabled(RagFeatureConfiguration global) {
        return flag(global, RagConfig::reasoningEnabled, RagFeatureConfiguration::isReasoningEnabled);
    }

    public static boolean rankerEnabled(RagFeatureConfiguration global) {
        return flag(global, RagConfig::rankerEnabled, RagFeatureConfiguration::isRankerEnabled);
    }

    public static boolean postRetrievalEnabled(RagFeatureConfiguration global) {
        return flag(global, RagConfig::postRetrievalEnabled, RagFeatureConfiguration::isPostRetrievalEnabled);
    }

    public static boolean functionCallingEnabled(RagFeatureConfiguration global) {
        return flag(global, RagConfig::functionCallingEnabled, RagFeatureConfiguration::isFunctionCallingEnabled);
    }

    public static boolean useRetrieval(RagFeatureConfiguration global) {
        return flag(global, RagConfig::useRetrieval, RagFeatureConfiguration::isUseRetrieval);
    }

    public static boolean useAdvisor(RagFeatureConfiguration global) {
        return flag(global, RagConfig::useAdvisor, RagFeatureConfiguration::isUseAdvisor);
    }

    @FunctionalInterface
    private interface ConfigBool {
        boolean get(RagConfig c);
    }

    @FunctionalInterface
    private interface GlobalBool {
        boolean get(RagFeatureConfiguration g);
    }

    private static boolean flag(RagFeatureConfiguration global, ConfigBool resolved, GlobalBool fallback) {
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx != null && ctx.resolvedConfig() != null) {
            return resolved.get(ctx.resolvedConfig());
        }
        return fallback.get(global);
    }
}
