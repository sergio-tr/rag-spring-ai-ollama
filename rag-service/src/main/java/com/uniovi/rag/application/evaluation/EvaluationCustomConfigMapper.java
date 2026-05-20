package com.uniovi.rag.application.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps Lab evaluation POST bodies to {@link RagFeatureConfiguration} + implementation overrides.
 */
@Component
public class EvaluationCustomConfigMapper {

    private final RagImplementationProperties defaultImplementationProperties;

    public EvaluationCustomConfigMapper(RagImplementationProperties defaultImplementationProperties) {
        this.defaultImplementationProperties = defaultImplementationProperties;
    }

    public FeatureAndImplementation fromRequestBody(Map<String, Object> config) {
        RagFeatureConfiguration customConfig = new RagFeatureConfiguration();
        customConfig.setExpansionEnabled(getBoolean(config, "expansion", false));
        customConfig.setNerEnabled(getBoolean(config, "ner", false));
        customConfig.setToolsEnabled(getBoolean(config, "tools", false));
        customConfig.setMetadataEnabled(getBoolean(config, "metadata", false));
        customConfig.setReasoningEnabled(getBoolean(config, "reasoning", false));
        customConfig.setRankerEnabled(getBoolean(config, "ranker", false));
        customConfig.setPostRetrievalEnabled(getBoolean(config, "post-retrieval", false));
        customConfig.setFunctionCallingEnabled(getBoolean(config, "function-calling", false));
        customConfig.setUseRetrieval(getBoolean(config, "use-retrieval", true));
        customConfig.setUseAdvisor(getBoolean(config, "use-advisor", true));
        RagImplementationProperties impl = RagImplementationProperties.copyOf(defaultImplementationProperties);
        if (config.get("query-service-impl") instanceof String s) {
            impl.setQueryServiceImpl(s);
        }
        if (config.get("retriever-impl") instanceof String s) {
            impl.setRetrieverImpl(s);
        }
        if (config.get("analyser-impl") instanceof String s) {
            impl.setAnalyserImpl(s);
        }
        return new FeatureAndImplementation(customConfig, impl);
    }

    /**
     * Metadata block embedded in evaluation JSON for clients (same shape as removed HTTP contract).
     */
    public Map<String, Object> implementationsBlock(RagFeatureConfiguration customConfig, RagImplementationProperties impl) {
        Map<String, Object> implementationEntries = new LinkedHashMap<>();
        implementationEntries.put("queryService", impl.getQueryServiceImpl() != null ? impl.getQueryServiceImpl() : "process");
        implementationEntries.put("retriever", impl.getRetrieverImpl() != null ? impl.getRetrieverImpl() : "basic");
        implementationEntries.put("analyser", impl.getAnalyserImpl() != null ? impl.getAnalyserImpl() : "minute-ner");
        implementationEntries.put("reasoningStrategy", "SIMPLE");
        implementationEntries.put("responseRanker", "LLM_AS_JUDGE");
        implementationEntries.put(
                "documentService",
                customConfig.isMetadataEnabled() ? "MetadataMinuteDocumentService" : "SimpleDocumentService");
        Map<String, Object> configWrapper = new HashMap<>();
        configWrapper.put("implementations", implementationEntries);
        return configWrapper;
    }

    private static boolean getBoolean(Map<String, Object> config, String key, boolean defaultValue) {
        Object v = config.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }

    public record FeatureAndImplementation(RagFeatureConfiguration features, RagImplementationProperties impl) {}
}
